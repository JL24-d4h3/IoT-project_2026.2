package com.taxi.gestion.exception;

import com.taxi.gestion.dto.response.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traduce excepciones a codigos HTTP coherentes para toda la API REST.
 * Solo aplica a @RestController: las vistas Thymeleaf manejan sus propios errores.
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiErrorResponse> noEncontrado(RecursoNoEncontradoException ex,
                                                        HttpServletRequest req) {
        return construir(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiErrorResponse> reglaNegocio(ReglaNegocioException ex,
                                                        HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    @ExceptionHandler({ConflictoEstadoException.class, RecursoDuplicadoException.class})
    public ResponseEntity<ApiErrorResponse> conflicto(RuntimeException ex,
                                                    HttpServletRequest req) {
        return construir(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiErrorResponse> credencialesInvalidas(CredencialesInvalidasException ex,
                                                                HttpServletRequest req) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler({CuentaNoOperativaException.class, AccesoNoAutorizadoException.class})
    public ResponseEntity<ApiErrorResponse> prohibido(RuntimeException ex,
                                                    HttpServletRequest req) {
        return construir(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    /** Errores de @Valid sobre los DTOs: 400 con el detalle campo a campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validacion(MethodArgumentNotValidException ex,
                                                    HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> errores.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));

        ApiErrorResponse cuerpo = new ApiErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Hay campos invalidos en la peticion",
                req.getRequestURI(),
                errores);
        return ResponseEntity.badRequest().body(cuerpo);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiErrorResponse> cuerpoIlegible(Exception ex, HttpServletRequest req) {
        return construir(HttpStatus.BAD_REQUEST,
                "El cuerpo o los parametros de la peticion no tienen el formato esperado", req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> generico(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrio un error inesperado procesando la peticion", req);
    }

    private ResponseEntity<ApiErrorResponse> construir(HttpStatus status, String mensaje,
                                                    HttpServletRequest req) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(
                status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI()));
    }
}
