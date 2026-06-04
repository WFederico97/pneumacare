package wfederico.pneumacare.clinical.web.dto;

import wfederico.pneumacare.clinical.domain.CstatInterpretation;

public record CstatResponse(
    double cstat,
    CstatInterpretation interpretation
) {
}
