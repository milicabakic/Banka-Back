package rs.edu.raf.banka.berza.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import rs.edu.raf.banka.berza.model.Akcije;
import rs.edu.raf.banka.berza.model.Berza;

import java.util.List;

public interface AkcijeRepository extends JpaRepository<Akcije, Long>, JpaSpecificationExecutor<Akcije> {

    Akcije findAkcijeByOznakaHartije(String oznaka);

    Akcije findAkcijeById(Long id);

    @Query("SELECT a FROM Akcije a WHERE (:berzaPrefix IS NULL OR LOWER(a.berza.naziv) LIKE CONCAT('%', :berzaPrefix)) AND " +
            "(:priceLowBound = 0 OR a.cena >= :priceLowBound) AND (:priceUpperBound = 0 OR a.cena <= :priceUpperBound) AND " +
            "(:askLowBound = 0 OR a.ask >= :askLowBound) AND (:askUpperBound = 0 OR a.ask <= :askUpperBound) AND " +
            "(:bidLowBound = 0 OR a.bid >= :bidLowBound) AND (:bidUpperBound = 0 OR a.bid <= :bidUpperBound) AND " +
            "(:volumeLowBound = 0 OR a.volume >= :volumeLowBound) AND (:volumeUpperBound = 0 OR a.volume <= :volumeUpperBound)")
    List<Akcije> filterAkcije(String berzaPrefix, Double priceLowBound, Double priceUpperBound, Double askLowBound, Double askUpperBound,
                              Double bidLowBound, Double bidUpperBound, Long volumeLowBound, Long volumeUpperBound);

}
