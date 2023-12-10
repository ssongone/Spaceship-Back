package jungle.spaceship.member.entity;


import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jungle.spaceship.member.controller.dto.SignUpDto;
import jungle.spaceship.member.entity.alien.Alien;
import jungle.spaceship.member.entity.family.Family;
import jungle.spaceship.member.entity.family.FamilyRole;
import jungle.spaceship.member.entity.family.Role;
import jungle.spaceship.member.entity.oauth.OAuthInfoResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDate;

@Getter
@NoArgsConstructor
@Entity
public class Member extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long memberId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column
    private String picture;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String nickname;

    @Enumerated(EnumType.STRING)
    private FamilyRole familyRole;

    private LocalDate birthdate;

    @OneToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "alien_id")
    private Alien alien;

    @ManyToOne
    @JoinColumn(name = "family_id")
    @JsonBackReference
    private Family family;

    @JsonIgnore
    private String firebaseToken;

    private int point;
    public void setFamily(Family family) {
        this.family = family;
    }

    @Builder
    public Member(String name, String email, String picture, Role role){
        this.name = name;
        this.email = email;
        this.picture = picture;
        this.role = role;
    }

    public Member(OAuthInfoResponse oAuthInfoResponse) {
        this.name = oAuthInfoResponse.getName();
        this.email = oAuthInfoResponse.getEmail();
        this.picture = oAuthInfoResponse.getPicture();
        this.role = Role.GUEST;
    }

    public String getRoleKey(){
        return this.role.getKey();
    }

    public void update(SignUpDto dto){
        this.nickname = dto.getNickname();
        this.familyRole = dto.getFamilyRole();
        this.birthdate = dto.getBirthdate();
        this.firebaseToken = dto.getFirebaseToken();
        this.role = Role.USER;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setAlien(Alien alien) {
        this.alien = alien;
    }

    public void setFirebaseToken(String firebaseToken) {
        this.firebaseToken = firebaseToken;
    }

    // 몇점 올랐는지 반환
    public int updatePoint(int xp) {
        int before = point;
        point += xp;
        if (point > 10) {
            System.out.println("하루 경험치 획득량 초과");
            point = 10;
        }
        return point - before;
    }
}
