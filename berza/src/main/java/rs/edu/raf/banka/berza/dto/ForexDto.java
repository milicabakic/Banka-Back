package rs.edu.raf.banka.berza.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForexDto {

    private Long id_hartije_od_vrednosti;
    private String oznaka_hartije;
    private Double cena;
    private Double promena_iznos;
    private Long volume;

}
