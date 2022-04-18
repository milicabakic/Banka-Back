package rs.edu.raf.banka.berza.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

import javax.persistence.*;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Akcije extends HartijaOdVrednosti{

    private Long outstandingShares;

    public double getPromenaProcenat() {
        return (100 * (super.getPromenaIznos()) / (super.getCena() - super.getPromenaIznos()) );
    }

    public double getDollarVolume () {
        return super.getVolume() * super.getCena();
    }

    public double getMarketCap() {
        return outstandingShares * super.getCena();
    }

}
