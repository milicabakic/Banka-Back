package rs.edu.raf.banka.berza.model;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import javax.persistence.*;

//@Entity
//@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
@MappedSuperclass // samo za kod, nema veze sa bazon
@Data
@AllArgsConstructor
@NoArgsConstructor
public abstract class HartijaOdVrednosti {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oznakaHartije;
    private String opisHartije;

    @ManyToOne
    private Berza berza;

    private Date lastUpdated;
    private Double cena;
    private Double ask;
    private Double bid;
    private Double promenaIznos;
    private Long volume;

    @ElementCollection
    private List<String> istorijskiPodaci;
}
