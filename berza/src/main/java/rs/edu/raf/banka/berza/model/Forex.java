package rs.edu.raf.banka.berza.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Forex extends HartijaOdVrednosti{

    @ManyToOne
    private Valuta baseCurrency;

    @ManyToOne
    private Valuta quoteCurrency;

    private Long contractSize;
    private Double lotSize;

    public double getNominalnaVrednost () {
        return lotSize * super.getCena();
    }
}
