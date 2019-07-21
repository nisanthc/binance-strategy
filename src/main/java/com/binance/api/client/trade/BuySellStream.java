package com.binance.api.client.trade;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;

public class BuySellStream {

	private final InfluxDB influxDB;
	private final String symbol;
	private String specificTime;
	private Map<String, BigDecimal> buySellDetails = new HashMap<String, BigDecimal>();

	public BuySellStream(String symbol) {

		this.symbol = symbol;
		this.specificTime = "";
		this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086");
	}

	private BigDecimal[] getHighestBIDS() {

		String queryString;
		BigDecimal[] highbids = new BigDecimal[2];

		queryString = String.format("select max(price), max(price)*97/100 as percent97 from orderbook "
				+ "where time = '%s' and symbol = '%s' " + "and category = 'BIDS';", specificTime, symbol);

		List<List<Object>> records = selectQueryDB(queryString);

		if (records != null) {
			for (List<Object> row : records) {
				highbids[0] = BigDecimal.valueOf((double) row.get(1));
				highbids[1] = BigDecimal.valueOf((double) row.get(2));
			}
		}

		return highbids;

	}

	private BigDecimal[] getLowestASKS() {

		String queryString;
		BigDecimal[] lowasks = new BigDecimal[2];

		queryString = String.format("select min(price), min(price)*103/100 as percent103 from orderbook "
				+ "where time = '%s' and symbol = '%s' " + "and category = 'ASKS';", specificTime, symbol);

		List<List<Object>> records = selectQueryDB(queryString);

		if (records != null) {
			for (List<Object> row : records) {
				lowasks[0] = BigDecimal.valueOf((double) row.get(1));
				lowasks[1] = BigDecimal.valueOf((double) row.get(2));
			}
		}

		return lowasks;

	}

	private BigDecimal getBTCVolume(BigDecimal minprice, BigDecimal maxprice, String category) {

		String queryString;

		BigDecimal btcvolume = new BigDecimal(0.0);

		queryString = String.format(" select sum(btc) as totalbtc from "
				+ "(select price*quantity as btc from orderbook where time = '%s' "
				+ "and symbol = '%s' and category = '%s' and price >= %s and price <= %s " + "group by category)",
				specificTime, symbol, category, minprice, maxprice);

		List<List<Object>> records = selectQueryDB(queryString);

		if (records != null) {
			for (List<Object> row : records) {
				btcvolume = BigDecimal.valueOf((double) row.get(1));
			}
		}

		return btcvolume;

	}

	private String getLatestLoadedTime() {

		String queryString;
		String latestLoadedTime = "";

		queryString = String.format("select time, price from orderbook where symbol = '%s' order by time desc limit 1;",
				symbol);

		List<List<Object>> records = selectQueryDB(queryString);

		if (records != null) {
			for (List<Object> row : records) {
				latestLoadedTime = (String) row.get(0);
			}
		}
//		System.out.println(latestLoadedTime);

		return latestLoadedTime;
	}

	private List<List<Object>> selectQueryDB(String queryString) {

		List<List<Object>> records = null;

//		System.out.println(queryString);

		Query query = new Query(queryString, "stock_order");

		List<QueryResult.Result> resutls = influxDB.query(query).getResults();

		if (resutls.get(0).getSeries() != null) {
			records = resutls.get(0).getSeries().get(0).getValues();
		}
		return records;
	}

	public Map<String, BigDecimal> getBuySellDetails() {

		String latestRecordedTime = getLatestLoadedTime();
	
		if (latestRecordedTime.equals(specificTime)) {
			
//			System.out.println("Return previously processed record " + buySellDetails );
			return buySellDetails;
			
		}
		specificTime = latestRecordedTime;

		BigDecimal[] highestBids = getHighestBIDS();
		BigDecimal[] lowestAsks = getLowestASKS();

		BigDecimal btcbidvolume = getBTCVolume(highestBids[1], highestBids[0], "BIDS");
		BigDecimal btcaskvolume = getBTCVolume(lowestAsks[0], lowestAsks[1], "ASKS");

		buySellDetails.put("highestBid", highestBids[0]);
		buySellDetails.put("lowestAsk", lowestAsks[0]);
		buySellDetails.put("btcBidVolume", btcbidvolume);
		buySellDetails.put("btcAskVolume", btcaskvolume);

//		System.out.println("Return new record " + buySellDetails );
		return buySellDetails;

	}

}
