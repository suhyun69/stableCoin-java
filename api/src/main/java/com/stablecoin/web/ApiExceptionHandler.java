package com.stablecoin.web;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.stablecoin.chain.application.ChainException;
import com.stablecoin.chain.application.ChainIdMismatchException;
import com.stablecoin.chain.application.ChainUnavailableException;
import com.stablecoin.chain.application.TransactionNotFoundException;
import com.stablecoin.chain.application.UnknownSignerException;
import com.stablecoin.payout.application.IdempotencyKeyConflictException;
import com.stablecoin.payout.application.PayoutNotFoundException;

/**
 * 모든 오류를 ProblemDetail(application/problem+json)로 반환한다. 응답의 code로 오류 종류를 구분한다.
 * 노드 주소 같은 내부 정보는 응답에 넣지 않고 로그에만 남긴다.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/** 노드에 연결할 수 없을 때 다시 시도하기까지 권장 대기 시간(초) */
	private static final String RETRY_AFTER_SECONDS = "5";

	/**
	 * 요청 본문 검증에 실패한 경우. 어떤 필드가 왜 잘못되었는지 detail에 담는다.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getFieldErrors()
			.stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.sorted()
			.collect(Collectors.joining(", "));
		return handleExceptionInternal(ex, invalidRequest(ex.getBody(), detail), headers, status, request);
	}

	/**
	 * 경로 변수 등 메서드 파라미터 검증에 실패한 경우.
	 */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
			.sorted()
			.collect(Collectors.joining(", "));
		return handleExceptionInternal(ex, invalidRequest(ex.getBody(), detail), headers, status, request);
	}

	/**
	 * 경로 변수의 형식이 잘못된 경우 (UUID가 아닌 지급 ID 등).
	 */
	@Override
	protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		String name = (ex instanceof MethodArgumentTypeMismatchException mismatch) ? mismatch.getName()
				: ex.getPropertyName();
		ProblemDetail body = ProblemDetail.forStatus(status);
		return handleExceptionInternal(ex, invalidRequest(body, name + ": 형식이 올바르지 않습니다"), headers, status,
				request);
	}

	/**
	 * 필수 헤더(Idempotency-Key 등)가 없는 경우.
	 */
	@Override
	protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		String detail = (ex instanceof MissingRequestHeaderException missing)
				? missing.getHeaderName() + ": 필수 헤더입니다" : ex.getBody().getDetail();
		return handleExceptionInternal(ex, invalidRequest(ex.getBody(), detail), headers, status, request);
	}

	/**
	 * 노드가 요청을 거부한 경우. 거부 사유(잔액 부족 등)는 클라이언트가 알아야 하므로 그대로 전달한다.
	 */
	@ExceptionHandler(ChainException.class)
	public ProblemDetail handleChainRejected(ChainException ex) {
		return problem(HttpStatus.BAD_REQUEST, ApiErrorCode.CHAIN_REJECTED, ex.getMessage());
	}

	/**
	 * 이미 쓴 멱등성 키로 다른 내용을 요청한 경우.
	 */
	@ExceptionHandler(IdempotencyKeyConflictException.class)
	public ProblemDetail handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex) {
		return problem(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_KEY_CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(PayoutNotFoundException.class)
	public ProblemDetail handlePayoutNotFound(PayoutNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, ApiErrorCode.PAYOUT_NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler(TransactionNotFoundException.class)
	public ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, ApiErrorCode.TRANSACTION_NOT_FOUND, ex.getMessage());
	}

	/**
	 * 노드에 연결할 수 없는 경우. 일시적 장애이므로 503과 Retry-After로 재시도를 안내한다.
	 */
	@ExceptionHandler(ChainUnavailableException.class)
	public ResponseEntity<ProblemDetail> handleChainUnavailable(ChainUnavailableException ex) {
		log.warn("Chain unavailable", ex);
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
			.body(problem(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.CHAIN_UNAVAILABLE,
					"chain is temporarily unavailable"));
	}

	/**
	 * 앱이 키를 갖고 있지 않은 지갑에서 보내려 한 경우.
	 */
	@ExceptionHandler(UnknownSignerException.class)
	public ProblemDetail handleUnknownSigner(UnknownSignerException ex) {
		return problem(HttpStatus.BAD_REQUEST, ApiErrorCode.UNKNOWN_SIGNER, ex.getMessage());
	}

	/**
	 * 노드의 체인 ID가 설정과 다른 경우. 잘못된 체인에 서명하지 않도록 전송을 거부한다.
	 */
	@ExceptionHandler(ChainIdMismatchException.class)
	public ProblemDetail handleChainIdMismatch(ChainIdMismatchException ex) {
		log.error("Refusing to sign: {}", ex.getMessage());
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.CHAIN_ID_MISMATCH,
				"connected chain does not match the configured chain");
	}

	/**
	 * 노드가 올바르지 않은 응답(HTTP 오류, 해석할 수 없는 본문 등)을 반환한 경우.
	 */
	@ExceptionHandler(RestClientException.class)
	public ProblemDetail handleChainError(RestClientException ex) {
		log.error("Unexpected chain response", ex);
		return problem(HttpStatus.BAD_GATEWAY, ApiErrorCode.CHAIN_ERROR, "chain returned an unexpected response");
	}

	private static ProblemDetail invalidRequest(ProblemDetail body, String detail) {
		body.setDetail(detail);
		body.setProperty("code", ApiErrorCode.INVALID_REQUEST);
		return body;
	}

	private static ProblemDetail problem(HttpStatus status, ApiErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setProperty("code", code);
		return problem;
	}

}
