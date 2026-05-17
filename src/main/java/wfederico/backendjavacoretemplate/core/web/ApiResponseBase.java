package wfederico.backendjavacoretemplate.core.web;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponseBase<T> {
    private Integer status;
    private String message;
    private T data;
    private String traceId;
}
