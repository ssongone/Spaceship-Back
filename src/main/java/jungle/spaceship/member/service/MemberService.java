package jungle.spaceship.member.service;


import jungle.spaceship.chat.entity.ChatRoom;
import jungle.spaceship.chat.repository.ChatRoomRepository;
import jungle.spaceship.jwt.JwtTokenProvider;
import jungle.spaceship.jwt.SecurityUtil;
import jungle.spaceship.jwt.TokenInfo;
import jungle.spaceship.member.controller.dto.*;
import jungle.spaceship.member.entity.Member;
import jungle.spaceship.member.entity.Plant;
import jungle.spaceship.member.entity.alien.Alien;
import jungle.spaceship.member.entity.family.Family;
import jungle.spaceship.member.entity.family.InvitationCode;
import jungle.spaceship.member.entity.family.Role;
import jungle.spaceship.member.entity.oauth.KakaoInfoResponse;
import jungle.spaceship.member.entity.oauth.OAuthInfoResponse;
import jungle.spaceship.member.repository.*;
import jungle.spaceship.response.ExtendedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.transaction.Transactional;
import java.security.SecureRandom;
import java.util.NoSuchElementException;
import java.util.Optional;

import static jungle.spaceship.member.entity.family.InvitationCode.CODE_CHARACTERS;
import static jungle.spaceship.member.entity.family.InvitationCode.CODE_LENGTH;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final AlienRepository alienRepository;
    private final FamilyRepository familyRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final PlantRepository plantRepository;
    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityUtil securityUtil;

    static String OAUTH2_URL_KAKAO = "https://kapi.kakao.com/v2/user/me";
    public ExtendedResponse<TokenInfo> loginWithKakao(String accessToken) {
        OAuthInfoResponse oAuthInfoResponse = requestOAuthInfo(accessToken);

        Optional<Member> memberByEmail = memberRepository.findByEmail(oAuthInfoResponse.getEmail());
        Member member = memberByEmail.orElseGet(() -> memberRepository.save(new Member(oAuthInfoResponse)));

        HttpStatus responseStatus = HttpStatus.CREATED;
        // 가족 정보 없는 경우 가짜 FamilyId 줌.. 별로 좋은 방법은 아닌것 같다..
        Long familyId = 0L;

        if (memberByEmail.isPresent()) {
            responseStatus = HttpStatus.OK;
            familyId = member.getFamily().getFamilyId();
        }

        TokenInfo tokenInfo = jwtTokenProvider.generateTokenByMember(member.getMemberId(), member.getRole().getKey(), familyId);
        System.out.println("tokenInfo.getAccessToken() = " + tokenInfo.getAccessToken());
        return new ExtendedResponse<>(tokenInfo, responseStatus.value(), "로그인 완료");
    }

    public OAuthInfoResponse requestOAuthInfo(String accessToken) {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        httpHeaders.set("Authorization", "Bearer " + accessToken);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("property_keys", "[\"kakao_account.email\", \"kakao_account.profile\"]");

        HttpEntity<?> request = new HttpEntity<>(body, httpHeaders);
        KakaoInfoResponse kakaoInfoResponse = restTemplate.postForObject(OAUTH2_URL_KAKAO, request, KakaoInfoResponse.class);
        System.out.println("kakaoInfoResponse = " + kakaoInfoResponse);

        return restTemplate.postForObject(OAUTH2_URL_KAKAO, request, KakaoInfoResponse.class);
    }

    public void signUp(SignUpDto dto) {
        Member member = securityUtil.extractMember();
        member.update(dto);
        System.out.println("member = " + member);
        memberRepository.save(member);
    }


    public void registerAlien(AlienDto dto) {
        Member member = securityUtil.extractMember();
        Alien alien = new Alien(dto);
        member.setAlien(alienRepository.save(alien));
        memberRepository.save(member);
    }

    @Transactional
    public ExtendedResponse<FamilyRegistrationDto> registerFamily(FamilyDto dto) {
        String code = dto.getCode();
        Member member = securityUtil.extractMember();
        ChatRoom chatRoom = new ChatRoom(dto.getCreatedAt());
        Plant plant = new Plant(dto.getPlantName());
        Family family = new Family(dto, chatRoom,plant);

        family.getMembers().add(member);
        member.setFamily(family);
        member.setRole(Role.USER);

        plantRepository.save(plant);
        chatRoomRepository.save(chatRoom);
        memberRepository.save(member);
        familyRepository.save(family);

        InvitationCode invitationCode = new InvitationCode(code, family);
        invitationCodeRepository.save(invitationCode);

        TokenInfo tokenInfo = jwtTokenProvider.generateTokenByMember(member.getMemberId(), member.getRole().getKey(),family.getFamilyId());
        FamilyResponseDto familyResponseDto = new FamilyResponseDto(family);
        FamilyRegistrationDto familyRegistrationDto = new FamilyRegistrationDto(tokenInfo, code, member, familyResponseDto);
        return new ExtendedResponse<>(familyRegistrationDto, HttpStatus.CREATED.value(), "가족이 생성되었습니다");

    }

    @Transactional
    public ExtendedResponse<FamilyRegistrationDto> registerCurrentFamily(String code) {
        InvitationCode invitationCode = invitationCodeRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("코드가 유효하지 않습니다"));

        Family family = invitationCode.getFamily();
        Member member = securityUtil.extractMember();
        member.setRole(Role.USER);
        member.setFamily(family);
        family.getMembers().add(member);

        memberRepository.save(member);
        familyRepository.save(family);

        TokenInfo tokenInfo = jwtTokenProvider.generateTokenByMember(member.getMemberId(), member.getRole().getKey(), family.getFamilyId());
        FamilyResponseDto familyResponseDto = new FamilyResponseDto(family);
        FamilyRegistrationDto familyRegistrationDto = new FamilyRegistrationDto(tokenInfo, code, member, familyResponseDto);

//        notificationService.sendMessageToFamilyExcludingMe(familyRegistrationDto, member);
        return new ExtendedResponse<>(familyRegistrationDto, HttpStatus.OK.value(), "가족에 등록되었습니다");
    }

    public String makeCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder codeBuilder = new StringBuilder(CODE_LENGTH);

        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = random.nextInt(CODE_CHARACTERS.length());
            char randomChar = CODE_CHARACTERS.charAt(randomIndex);
            codeBuilder.append(randomChar);
        }

        String code = codeBuilder.toString();
        if (invitationCodeRepository.findByCode(code).isPresent()) {
            code = makeCode();
        }
        return code;
    }

}
