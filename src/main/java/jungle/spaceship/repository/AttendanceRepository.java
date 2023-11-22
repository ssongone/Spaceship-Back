package jungle.spaceship.repository;

import jungle.spaceship.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
     List<Attendance> findByMember_Family_FamilyIdAndAttendanceTimeAfter(Long familyId, LocalDateTime today);
}
