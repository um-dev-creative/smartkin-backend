package com.umdc.smartkin.security.verification;

import com.umdc.smartkin.constant.SmartKinAppConstants;
import com.umdc.smartkin.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Objects;

import static com.umdc.security.constant.ConstantApp.SESSION_TOKEN_KEY;

/// Keeps a user who did not verify the e-mail out of the API. The session is backbone's token, as in zerobound; whether its user confirmed
/// the e-mail code is asked to [VerificationStatus] (mercury). Until then only what a visitor needs to get verified is open: reading the
/// own account, confirming or asking again for the code, and the verification status. It runs after the session interceptor of the
/// security library, which has already validated the token, so the `uid` is read from a token that is known to be genuine.
///
/// New accounts are inactive until the code is confirmed and cannot sign in before (backbone refuses them), so in practice this only
/// stops accounts that were created active and never verified.
public class VerifiedSessionInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final String apiPath;
    private final boolean required;
    private final List<String> openPaths;
    private final VerificationStatus verificationStatus;

    /// Builds the interceptor.
    ///
    /// @param apiPath  the base path of the API (`umdc.api.endpoint`)
    /// @param required `false` switches the check off (accounts that predate the verification)
    /// @param verificationStatus whether a user confirmed the e-mail code
    public VerifiedSessionInterceptor(String apiPath, boolean required, VerificationStatus verificationStatus) {
        this.apiPath = apiPath;
        this.required = required;
        this.verificationStatus = verificationStatus;
        this.openPaths = List.of(apiPath + "/auth/**", apiPath + "/user-register/**", apiPath + "/users/{id}");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!required || isOpen(request)) {
            return true;
        }
        var userId = JwtUtil.getUidFromToken(request.getHeader(SESSION_TOKEN_KEY));
        if (Objects.nonNull(userId) && verificationStatus.isVerified(userId)) {
            return true;
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(SmartKinAppConstants.MESSAGE_ERROR_HEADER, SmartKinAppConstants.VERIFICATION_PENDING_MESSAGE);
        return false;
    }

    private boolean isOpen(HttpServletRequest request) {
        var method = request.getMethod();
        var path = request.getRequestURI();
        if (HttpMethod.OPTIONS.matches(method) || (HttpMethod.POST.matches(method) && MATCHER.match(apiPath + "/users", path))) {
            return true;
        }
        return openPaths.stream().anyMatch(open -> MATCHER.match(open, path) && (!open.endsWith("/users/{id}") || HttpMethod.GET.matches(method)));
    }
}
