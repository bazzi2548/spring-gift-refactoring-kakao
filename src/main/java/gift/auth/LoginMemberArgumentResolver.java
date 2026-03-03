package gift.auth;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import gift.member.Member;
import jakarta.servlet.http.HttpServletRequest;

@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

	private final AuthenticationResolver authenticationResolver;

	public LoginMemberArgumentResolver(AuthenticationResolver authenticationResolver) {
		this.authenticationResolver = authenticationResolver;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return parameter.hasParameterAnnotation(LoginMember.class)
			&& parameter.getParameterType().equals(Member.class);
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
		NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		String authorization = request.getHeader("Authorization");

		Member member = authenticationResolver.extractMember(authorization);

		if (member == null) {
			throw new IllegalStateException("Unauthorized");
		}

		return member;
	}
}
