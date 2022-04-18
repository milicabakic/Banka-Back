package rs.edu.raf.banka.berza.model;

import javax.persistence.*;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FuturesUgovori extends HartijaOdVrednosti{

    private Double contractSize;
    private String contractUnit;
    private Double maintenanceMargin;
    private Date settlementDate;

    //TODO: Dodati izvedene informacije koje su opisane u specifikaciji.
}
