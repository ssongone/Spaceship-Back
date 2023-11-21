package jungle.spaceship.repository.mole;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MoleGameRepository extends JpaRepository<MoleGameRepository, Long> {
}