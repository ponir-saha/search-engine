package com.search.engine.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiFeedbackRequest {
	@NotBlank
	@Size(max = 1000)
	private String query;

	@Size(max = 40)
	private String mode;

	private Long productId;

	private Boolean relevant;

	private Boolean grounded;

	@Min(1)
	@Max(5)
	private Integer rating;

	@Size(max = 2000)
	private String comment;
}
