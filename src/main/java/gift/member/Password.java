package gift.member;

import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class Password {

	@Column
	private String password;

	protected Password() {}

	public Password(String password, PasswordEncoder encoder) {
		this.password = encoder.encode(password);
	}

	public boolean matches(String password, PasswordEncoder encoder) {
		return encoder.matches(password, this.password);
	}

	public String getPassword() {
		return password;
	}
}

