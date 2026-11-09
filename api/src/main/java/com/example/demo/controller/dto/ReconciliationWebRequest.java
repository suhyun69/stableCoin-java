package com.example.demo.controller.dto;
import jakarta.validation.constraints.*;
public record ReconciliationWebRequest(@NotBlank @Pattern(regexp="[0-9]{1,19}") String fromBlock,@NotBlank @Pattern(regexp="[0-9]{1,19}") String toBlock) {}
