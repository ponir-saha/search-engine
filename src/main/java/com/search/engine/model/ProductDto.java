package com.search.engine.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDto {
	private Long id;

	@NotBlank
	@Size(max = 300)
	private String name;

	@NotBlank
	@Size(max = 4000)
	private String description;

	@PositiveOrZero
	private Double price;
}
