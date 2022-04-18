package rs.edu.raf.banka.berza.requests;

import lombok.Data;

@Data
public class OrderRequest {

//    private Long berza_id;
    private Long userId;
//    private Long hartija_od_vrednosti_id;
    private String symbol;
    private String hartijaOdVrednostiTip;
    private Integer kolicina;
    private String akcija;
    private Integer limitValue;
    private Integer stopValue;
    private boolean allOrNoneFlag;
    private boolean marginFlag;

}
