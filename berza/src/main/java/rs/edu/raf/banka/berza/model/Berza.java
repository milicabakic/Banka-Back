package rs.edu.raf.banka.berza.model;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Berza {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String oznakaBerze;
    private String naziv;
    private String drzava;

    @ManyToOne
    private Valuta valuta;

    private String vremenskaZona;
    private String preMarketRadnoVreme;
    private String postMarketRadnoVreme;

    @ElementCollection
    private List<Date> praznici;

    @OneToMany
    private List<Order> orderi;

}
