package com.search.engine.api;

import com.search.engine.model.PageResponse;
import com.search.engine.model.ProductDto;
import com.search.engine.model.ProductSyncResult;
import com.search.engine.model.SemanticStatusResult;
import com.search.engine.model.SuggestionResponse;
import com.search.engine.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService service;

	public ProductController(ProductService service) {
		this.service = service;
	}

	@PostMapping("/reindex")
	public Mono<ProductSyncResult> reindex() {
		return service.syncProducts();
	}

	@PostMapping(value = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ProductSyncResult> sync() {
		return service.syncProducts();
	}

	@GetMapping(value = "/semantic-status", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<SemanticStatusResult> semanticStatus() {
		return service.semanticStatus();
	}

	@GetMapping(value = "/suggestions", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<SuggestionResponse> suggestions(@RequestParam String q,
												@RequestParam(defaultValue = "5") int size) {
		return service.suggestions(q, size);
	}

	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<PageResponse<ProductDto>> list(@RequestParam(defaultValue = "0") int page,
											   @RequestParam(defaultValue = "10") int size) {
		return service.list(page, size);
	}

	@GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ProductDto> get(@PathVariable Long id) {
		return service.get(id);
	}

	@ResponseStatus(HttpStatus.CREATED)
	@PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ProductDto> create(@RequestBody ProductDto product) {
		return service.create(product);
	}

	@PutMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<ProductDto> update(@PathVariable Long id, @RequestBody ProductDto product) {
		return service.update(id, product);
	}

	@ResponseStatus(HttpStatus.NO_CONTENT)
	@DeleteMapping("/{id}")
	public Mono<Void> delete(@PathVariable Long id) {
		return service.delete(id);
	}

	@GetMapping(value = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<PageResponse<ProductDto>> search(@RequestParam String q,
												 @RequestParam(defaultValue = "0") int page,
												 @RequestParam(defaultValue = "10") int size) {
		return service.search(q, page, size);
	}

	@GetMapping(value = "/search/opensearch", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<PageResponse<ProductDto>> searchOpenSearch(@RequestParam String q,
														   @RequestParam(defaultValue = "0") int page,
														   @RequestParam(defaultValue = "10") int size) {
		return service.searchOpenSearch(q, page, size);
	}

	@GetMapping(value = "/search/ai", produces = MediaType.APPLICATION_JSON_VALUE)
	public Mono<PageResponse<ProductDto>> searchAi(@RequestParam String q,
												   @RequestParam(defaultValue = "0") int page,
												   @RequestParam(defaultValue = "10") int size) {
		return service.searchAi(q, page, size);
	}
}
