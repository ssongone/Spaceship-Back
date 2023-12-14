package jungle.spaceship.tmi.service;

import jungle.spaceship.jwt.SecurityUtil;
import jungle.spaceship.member.controller.dto.PlantStateDto;
import jungle.spaceship.member.entity.Member;
import jungle.spaceship.member.service.PlantService;
import jungle.spaceship.notification.FcmService;
import jungle.spaceship.notification.NotificationType;
import jungle.spaceship.response.BasicResponse;
import jungle.spaceship.response.ExtendedResponse;
import jungle.spaceship.tmi.controller.dto.TmiDto;
import jungle.spaceship.tmi.controller.dto.TmiResponseDto;
import jungle.spaceship.tmi.entity.Attendance;
import jungle.spaceship.tmi.entity.Tmi;
import jungle.spaceship.tmi.repository.AttendanceRepository;
import jungle.spaceship.tmi.repository.TmiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jungle.spaceship.member.entity.Plant.ATTENDANCE_POINT;
import static jungle.spaceship.member.entity.Plant.TMI_POINT;

@Slf4j
@Service
@RequiredArgsConstructor
public class TmiService {

    private final SecurityUtil securityUtil;
    private final TmiRepository tmiRepository;
    private final AttendanceRepository attendanceRepository;
    private final PlantService plantService;
    private final FcmService fcmService;
    public BasicResponse tmiCheck() {
        Member member = securityUtil.extractMember();
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        Long count = tmiRepository.countByMemberAndCreateAtIsAfterAndCreateAtIsBefore(member, startOfDay, endOfDay);
        if (count < 1) {
            return new BasicResponse(HttpStatus.NOT_ACCEPTABLE.value(), "오늘의 tmi를 작성하지 않았습니다.");
        }
        return new BasicResponse(HttpStatus.OK.value(), "오늘의 tmi를 작성했습니다.");
    }
    public BasicResponse registerTmi(TmiDto tmiDto) {
        Member member = securityUtil.extractMember();

        Tmi tmi = new Tmi(tmiDto, member);
        tmiRepository.save(tmi);

        plantService.performActivity(member, TMI_POINT);
        fcmService.sendFcmMessageToFamilyExcludingMe(member, NotificationType.TMI, tmiDto.getContent());

        return new BasicResponse(HttpStatus.CREATED.value(), "Tmi 등록 완료");
    }

    public List<TmiResponseDto> getTmiByFamilyId() {
        Long familyId = securityUtil.extractFamilyId();
        LocalDateTime today = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        List<TmiResponseDto> collect = tmiRepository.findByMember_Family_FamilyIdAndCreateAtIsAfterOrderByCreateAtDesc(familyId, today)
                .stream()
                .map(Tmi::toResponseDto)
                .collect(Collectors.toList());
        return collect;
    }

    @Transactional
    public ExtendedResponse<PlantStateDto> attend() {
        Member member = securityUtil.extractMember();

        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        PlantStateDto plantStateDto = new PlantStateDto();
        if (!attendanceRepository.findByMemberAndAttendanceTimeIsAfterAndAttendanceTimeIsBefore(member, startOfDay, endOfDay).isEmpty()) {
            return new ExtendedResponse<>(plantStateDto, HttpStatus.NOT_ACCEPTABLE.value(), "출석은 하루에 한번만 가능합니다");
        }

        Attendance attendance = new Attendance(member);
        attendanceRepository.save(attendance);

        plantService.performActivity(member, ATTENDANCE_POINT);
        return new ExtendedResponse<>(plantStateDto, HttpStatus.OK.value(), "출석 완료!");
    }

    public ExtendedResponse<List<Attendance>> getAttendees() {
        Long familyId = securityUtil.extractFamilyId();
        LocalDateTime today = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        List<Attendance> attendances = attendanceRepository.findByMember_Family_FamilyIdAndAttendanceTimeIsAfter(familyId, today);
        return new ExtendedResponse<>(attendances, HttpStatus.OK.value(), "");
    }


    public ExtendedResponse<Map<String, List<Tmi>>> weeklyTmi() {
        Long familyId = securityUtil.extractFamilyId();
        LocalDate startDate = LocalDate.now().minusWeeks(1);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Object[]> tmiWithDate = tmiRepository.findTmiDataByFamilyAndDate(familyId, startDateTime);
        Map<String, List<Tmi>> resultMap = tmiWithDate.stream()
                .collect(Collectors.groupingBy(
                        row -> row[0].toString(),
                        Collectors.mapping(row -> (Tmi) row[1], Collectors.toList())
                ));
        return new ExtendedResponse<>(resultMap, HttpStatus.OK.value(), "");
    }

    public ExtendedResponse<Map<String, List<Attendance>>> weeklyAttendance() {
        Long familyId = securityUtil.extractFamilyId();

        LocalDate startDate = LocalDate.now().minusWeeks(1);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        List<Object[]> attendanceWithDate = attendanceRepository.findAttendanceTimeByFamilyAndDate(familyId, startDateTime);
        Map<String, List<Attendance>> resultMap = attendanceWithDate.stream()
                .collect(Collectors.groupingBy(
                        row -> row[0].toString(),
                        Collectors.mapping(row -> (Attendance) row[1], Collectors.toList())

                ));
        return new ExtendedResponse<>(resultMap, HttpStatus.OK.value(), "");
    }


}
