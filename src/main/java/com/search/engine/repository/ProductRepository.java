package com.search.engine.repository;

import com.search.engine.model.ProductDto;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class ProductRepository {

	private final DatabaseClient db;

	public ProductRepository(DatabaseClient db) {
		this.db = db;
	}

	public Mono<Void> createTableIfNotExists() {
		String ddl = "CREATE TABLE IF NOT EXISTS products (id BIGINT PRIMARY KEY, name TEXT, description TEXT, price DOUBLE PRECISION);";
		return db.sql(ddl).then();
	}

	public Mono<Void> truncate() {
		return db.sql("TRUNCATE TABLE products").then();
	}

	public Mono<Long> count() {
		return db.sql("SELECT count(*) FROM products")
				.map(row -> {
					Number value = row.get(0, Number.class);
					return value == null ? 0L : value.longValue();
				})
				.one();
	}

	public Mono<Long> nextId() {
		return db.sql("SELECT COALESCE(MAX(id), 0) + 1 FROM products")
				.map(row -> {
					Number value = row.get(0, Number.class);
					return value == null ? 1L : value.longValue();
				})
				.one();
	}

	public Mono<Void> save(ProductDto p) {
		// Use a direct connection + positional binds to avoid driver-specific named-parameter
		// expansion issues (some drivers may wrap values in Parameter objects that then
		// fail encoding). Positional binding with the statement is more explicit.
		String sql = "INSERT INTO products(id,name,description,price) VALUES($1,$2,$3,$4) " +
				"ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, description = EXCLUDED.description, price = EXCLUDED.price";

		Long id = p.getId() == null ? 0L : p.getId();
		String name = p.getName() == null ? "" : p.getName();
		String desc = p.getDescription() == null ? "" : p.getDescription();
		Double price = p.getPrice() == null ? 0.0 : p.getPrice();

		return db.inConnection(connection ->
				Mono.from(connection.createStatement(sql)
						.bind(0, id)
						.bind(1, name)
						.bind(2, desc)
						.bind(3, price)
						.execute())
						.flatMap(result -> Mono.from(result.getRowsUpdated()))
						.then());
	}

	public Flux<ProductDto> findAll() {
		return db.sql("SELECT id, name, description, price FROM products ORDER BY id")
				.map(row -> new ProductDto(row.get("id", Long.class), row.get("name", String.class), row.get("description", String.class), row.get("price", Double.class)))
				.all();
	}

	public Flux<ProductDto> findPage(int page, int size) {
		int limit = Math.max(size, 1);
		int offset = Math.max(page, 0) * limit;
		return db.inConnectionMany(connection ->
				Flux.from(connection.createStatement("SELECT id, name, description, price FROM products ORDER BY id LIMIT $1 OFFSET $2")
						.bind(0, limit)
						.bind(1, offset)
						.execute())
						.flatMap(result -> result.map((row, metadata) -> toProduct(row))));
	}

	public Mono<ProductDto> findById(Long id) {
		return db.inConnection(connection ->
				Mono.from(connection.createStatement("SELECT id, name, description, price FROM products WHERE id = $1")
						.bind(0, id)
						.execute())
						.flatMapMany(result -> result.map((row, metadata) -> toProduct(row)))
						.singleOrEmpty());
	}

	public Mono<Boolean> existsById(Long id) {
		return db.inConnection(connection ->
				Mono.from(connection.createStatement("SELECT EXISTS(SELECT 1 FROM products WHERE id = $1)")
						.bind(0, id)
						.execute())
						.flatMapMany(result -> result.map((row, metadata) -> Boolean.TRUE.equals(row.get(0, Boolean.class))))
						.single(false));
	}

	public Mono<Boolean> deleteById(Long id) {
		return db.inConnection(connection ->
				Mono.from(connection.createStatement("DELETE FROM products WHERE id = $1")
						.bind(0, id)
						.execute())
						.flatMap(result -> Mono.from(result.getRowsUpdated()))
							.map(rows -> rows > 0));
	}

	private ProductDto toProduct(io.r2dbc.spi.Row row) {
		return new ProductDto(
				row.get("id", Long.class),
				row.get("name", String.class),
				row.get("description", String.class),
				row.get("price", Double.class)
		);
	}

}
