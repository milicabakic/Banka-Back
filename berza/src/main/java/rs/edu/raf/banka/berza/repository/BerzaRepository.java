package rs.edu.raf.banka.berza.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rs.edu.raf.banka.berza.model.Berza;

public interface BerzaRepository extends JpaRepository<Berza, Long> {

    Berza findBerzaById(Long id);

    Berza findBerzaByOznakaBerze(String oznakaBerze);
}
