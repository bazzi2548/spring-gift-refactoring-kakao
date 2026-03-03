package gift.member;

public class MemberFixture {

    public static Member member(Long id, String email) {
        return new Member(id, email);
    }
}
