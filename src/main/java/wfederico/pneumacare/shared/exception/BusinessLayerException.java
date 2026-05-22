package wfederico.pneumacare.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessLayerException extends RuntimeException {
    private final HttpStatus statusCode;

    public BusinessLayerException(String message, HttpStatus statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
