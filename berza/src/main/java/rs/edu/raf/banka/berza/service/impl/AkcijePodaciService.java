package rs.edu.raf.banka.berza.service.impl;

import com.crazzyghost.alphavantage.AlphaVantage;
import com.crazzyghost.alphavantage.Config;
import com.crazzyghost.alphavantage.fundamentaldata.response.CompanyOverview;
import com.crazzyghost.alphavantage.fundamentaldata.response.CompanyOverviewResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.*;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import rs.edu.raf.banka.berza.dto.AkcijePodaciDto;
import rs.edu.raf.banka.berza.dto.AkcijeTimeseriesDto;
import rs.edu.raf.banka.berza.dto.request.AkcijeTimeseriesReadRequest;
import rs.edu.raf.banka.berza.dto.request.AkcijeTimeseriesUpdateRequest;
import rs.edu.raf.banka.berza.model.Akcije;
import rs.edu.raf.banka.berza.model.Berza;
import rs.edu.raf.banka.berza.repository.AkcijeRepository;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static java.time.temporal.TemporalAdjusters.firstDayOfYear;

@Service
public class AkcijePodaciService {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final WebClient influxApiClient;
    private final Config alphavantageApiClient;

    private AkcijeRepository akcijeRepository;
    private BerzaService berzaService;

    private final List<String> odabraneAkcije = Arrays.asList("AAPL", "MSFT", "GOOG", "BA", "AXP");

    @Autowired
    public AkcijePodaciService(AkcijeRepository akcijeRepository, BerzaService berzaService,
                               WebClient influxApiClient,
                               Config alphavantageApiClient){
        this.akcijeRepository = akcijeRepository;
        this.berzaService = berzaService;
        this.influxApiClient = influxApiClient;
        this.alphavantageApiClient = alphavantageApiClient;
    }

    public List<AkcijePodaciDto> getOdabraneAkcije() {
        ArrayList<AkcijePodaciDto> dtos = new ArrayList<>();
        for(String akcija: odabraneAkcije) {
            AkcijePodaciDto dto = getAkcijaByTicker(akcija);
            dtos.add(dto);
        }

        return dtos;
    }

    public AkcijePodaciDto getAkcijaByTicker(String ticker) {
        Akcije akcija = akcijeRepository.findAkcijeByOznakaHartije(ticker);
        // TODO: Dodati neki data decline, kada će informacije da se refreshuju, npr. posle dva dana.
        if (akcija == null) {
            akcija = new Akcije();
            CompanyOverviewResponse cor = AlphaVantage
                    .api()
                    .fundamentalData()
                    .companyOverview()
                    .forSymbol(ticker)
                    .fetchSync();
            if(cor != null && cor.getErrorMessage() == null) {
                CompanyOverview co = cor.getOverview();
                //uvezati sa Berzom
                Berza berza = berzaService.findBerza(co.getExchange());
                akcija.setBerza(berza);
                akcija.setOznakaHartije(co.getSymbol());
                akcija.setOznakaHartije("");
                akcija.setOpisHartije(co.getName());
                akcija.setLastUpdated(new Date());
                akcija.setOutstandingShares(co.getSharesOutstanding());
            } else {
                // Nećemo da pucamo u slučaju da se desila API greška.
                // API je rate limitovan, pa bi to dosta kočilo.
                // U produkciji svakako treba izbaciti grešku.
                akcija.setOznakaHartije(ticker);
                akcija.setOpisHartije(ticker);
                akcija.setLastUpdated(new Date());
                akcija.setOutstandingShares(0L);
            }
            akcijeRepository.save(akcija);
        }

        List<String> symbols = Arrays.asList(ticker);
        HashMap<String, List<String>> req = new HashMap<>();
        req.put("symbols", symbols);

        List<AkcijePodaciDto> dtoList = influxApiClient
            .post()
            .uri("/alphavantage/stock/quote/updateread/")
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromObject(req))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<AkcijePodaciDto>>() {})
            .block(REQUEST_TIMEOUT);
        if(dtoList == null || dtoList.size() == 0) {
            return null;
        }

        AkcijePodaciDto dto = dtoList.get(0);
        dto.setTicker(ticker);
        dto.setOpisHartije(akcija.getOpisHartije());
        dto.setOutstandingShares(akcija.getOutstandingShares());

        return dto;
    }

    public List<AkcijeTimeseriesDto> getAkcijeTimeseries(AkcijeTimeseriesUpdateRequest req) {
        influxApiClient
                .post()
                .uri("/alphavantage/stock/updatewait/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromObject(req))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AkcijePodaciDto>>() {})
                .block(REQUEST_TIMEOUT);

        DateTimeFormatter startFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'00:00:00.000'Z'");
        DateTimeFormatter endFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        ZonedDateTime zonedDateTime = ZonedDateTime.now();
        String endDate = zonedDateTime.format(endFormatter);

        if(req.getType().equals("intraday") && req.getInterval().equals("5min")) {
            switch (zonedDateTime.getDayOfWeek()) {
                case SATURDAY:
                case SUNDAY:
                    zonedDateTime = zonedDateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
                    break;
                case MONDAY:
                    if (zonedDateTime.getHour() < 16) {
                        zonedDateTime = zonedDateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.FRIDAY));
                    }
                    break;
            }
        } else if(req.getType().equals("intraday") && req.getInterval().equals("30min")) {
            switch (zonedDateTime.getDayOfWeek()) {
                case SATURDAY:
                case SUNDAY:
                    zonedDateTime = zonedDateTime.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    break;
                case MONDAY:
                    zonedDateTime = zonedDateTime.with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
                    break;
                default:
                    zonedDateTime = zonedDateTime.minusDays(7); // 7 zbog vikenda
            }
        } else {
            switch (req.getRequestType()) {
                case "1m":
                    zonedDateTime = zonedDateTime.minusMonths(1);
                    break;
                case "6m":
                    zonedDateTime = zonedDateTime.minusMonths(6);
                    break;
                case "1y":
                    zonedDateTime = zonedDateTime.minusMonths(12);
                    break;
                case "2y":
                    zonedDateTime = zonedDateTime.minusMonths(24);
                    break;
                case "ytd":
                    zonedDateTime = zonedDateTime.with(firstDayOfYear());
                    break;
            }
        }

        String startDate = zonedDateTime.format(startFormatter);

        AkcijeTimeseriesReadRequest readReq = new AkcijeTimeseriesReadRequest();
        readReq.setType(req.getType());
        readReq.setSymbol(req.getSymbol());
        readReq.setInterval(req.getInterval());
        readReq.setTimeFrom(startDate);
        readReq.setTimeTo(endDate);

        return influxApiClient
                .post()
                .uri("/alphavantage/stock/read/")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromObject(readReq))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<AkcijeTimeseriesDto>>() {})
                .block(REQUEST_TIMEOUT);
    }

    public Page<Akcije> search(String oznakaHartije, String opisHartije, Integer page, Integer size){
        Akcije akcije = new Akcije();
        akcije.setOznakaHartije(oznakaHartije);
        akcije.setOpisHartije(opisHartije);

        ExampleMatcher exampleMatcher = ExampleMatcher.matching()
                .withMatcher("oznakaHartije", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase())
                .withMatcher("opisHartije", ExampleMatcher.GenericPropertyMatchers.contains().ignoreCase());
        Example<Akcije> example = Example.of(akcije, exampleMatcher);

        return akcijeRepository.findAll(example, PageRequest.of(page, size));
    }

    public Page<Akcije> filter(String berzaPrefix, Double priceLowBound, Double priceUpperBound, Double askLowBound, Double askUpperBound,
                               Double bidLowBound, Double bidUpperBound, Long volumeLowBound, Long volumeUpperBound, Integer page, Integer size){
        List<Akcije> akcije = akcijeRepository.filterAkcije(berzaPrefix, priceLowBound, priceUpperBound, askLowBound, askUpperBound, bidLowBound, bidUpperBound, volumeLowBound, volumeUpperBound);
        return new PageImpl<Akcije>(akcije, PageRequest.of(page, size), akcije.size());
    }

    public Akcije getByID(Long id){
        return akcijeRepository.findAkcijeById(id);
    }

    public Berza findBerzaForAkcije(Long id){
        Akcije akcije = akcijeRepository.findAkcijeById(id);
        return akcije.getBerza();
    }
}
