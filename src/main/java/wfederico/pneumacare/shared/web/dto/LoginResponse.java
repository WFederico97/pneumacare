package wfederico.pneumacare.shared.web.dto;

import java.util.List;

/** Non-sensitive profile payload returned on successful login. Never contains the token. */
public record LoginResponse(
        String displayName,
        List<String> roles) {
}
