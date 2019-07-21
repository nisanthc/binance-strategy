package com.binance.api.client.trade;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.constant.BinanceApiConstants;
import com.binance.api.client.domain.OrderSide;
import com.binance.api.client.domain.OrderStatus;
import com.binance.api.client.domain.OrderType;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;;

public class OrderStrategy {

	private static final BigDecimal buyPrecent = new BigDecimal("0.005");
	private static final BigDecimal sellPrecent = new BigDecimal("0.05");
	private static final BigDecimal stopPrecent = new BigDecimal("0.07");
	private static final BigDecimal sellDropPrecent = new BigDecimal("0.01");
	private static final MathContext PRECISION = new MathContext(5, RoundingMode.HALF_EVEN);

	private final String symbol;
	private final BinanceApiRestClient restClient;
	private final InfluxDB influxDB;
	private final String runMode;

	public OrderStrategy(String symbol, String runMode, BinanceApiRestClient restClient) {

		this.symbol = symbol;
		this.runMode = runMode;
		this.restClient = restClient;
		this.influxDB = InfluxDBFactory.connect("http://127.0.0.1:8086");

	}

	private void postOrderToBinance(OrderSide side, OrderType type, TimeInForce timeInForce, String price,
			String stopPrice) {

		NewOrder newOrder = new NewOrder(symbol, side, type, timeInForce, String.valueOf(1), price);

		newOrder.newClientOrderId(String.valueOf(System.currentTimeMillis()));
		newOrder.stopPrice(stopPrice);

		try {

			BatchPoints batchpoint = BatchPoints.database("stock_order").retentionPolicy("stock_retention").build();

			if (runMode.equalsIgnoreCase("real")) {

				NewOrderResponse newOrderResponse = restClient.newOrder(newOrder);

				Point response = Point.measurement("order_response")
						.time(System.currentTimeMillis(), TimeUnit.MILLISECONDS).tag("symbol", symbol)
						.tag("orderside", String.valueOf(newOrderResponse.getSide()))
						.tag("ordertype", String.valueOf(newOrderResponse.getType()))
						.addField("price", newOrderResponse.getPrice())
						.addField("clientrequestid", newOrderResponse.getClientOrderId())
						.addField("orgqty", newOrderResponse.getOrigQty())
						.addField("exeqty", newOrderResponse.getExecutedQty())
						.addField("cumquoteqty", newOrderResponse.getCummulativeQuoteQty())
						.addField("status", String.valueOf(newOrderResponse.getStatus()))
						.addField("transacttime", newOrderResponse.getTransactTime())
						.addField("orderid", newOrderResponse.getOrderId()).build();

				batchpoint.point(response);
				influxDB.write(batchpoint);

			} else {

				restClient.newOrderTest(newOrder);
			}

			Point request = Point.measurement("order_request").time(System.currentTimeMillis(), TimeUnit.MILLISECONDS)
					.tag("symbol", symbol).tag("orderside", String.valueOf(side)).tag("ordertype", String.valueOf(type))
					.addField("price", price != null ? price : stopPrice)
					.addField("clientrequestid", newOrder.getNewClientOrderId()).build();

			batchpoint.point(request);
			influxDB.write(batchpoint);

		} catch (Exception e) {

			System.out.print(" || Message from Binance API :: " + e.getMessage());

		}

	}

	public void placeOrders(BigDecimal highestBid, BigDecimal lowestAsk) {

//		Place buy order 0.5% above  the current average price; done
//		Set a sell price to 5% above the buy price and Set stop loss to -7% below buy price done
//		After 20 seconds drop the sell price to 1% above the buy price;

		System.out.println("************** Placing Orders************");

		BigDecimal avgDivisor = new BigDecimal("2");
		BigDecimal avgPrice = highestBid.add(lowestAsk, PRECISION).divide(avgDivisor, PRECISION);
		BigDecimal buyPrice = avgPrice.multiply(buyPrecent, PRECISION).add(avgPrice, PRECISION);
		BigDecimal sellPrice = buyPrice.multiply(sellPrecent, PRECISION).add(buyPrice, PRECISION);
		BigDecimal stopPrice = buyPrice.subtract(buyPrice.multiply(stopPrecent, PRECISION), PRECISION);
		BigDecimal sellPriceDrop = buyPrice.multiply(sellDropPrecent, PRECISION).add(buyPrice, PRECISION);

		System.out.print(" Step 1 : Placing Buy order : Price :: " + buyPrice);
		postOrderToBinance(OrderSide.BUY, OrderType.LIMIT, TimeInForce.GTC, String.valueOf(buyPrice), null);

		System.out.print("\n Step 2 : Placing Sell order : Price :: " + sellPrice);
		postOrderToBinance(OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, String.valueOf(sellPrice), null);

		System.out.print("\n Step 3 : Placing Stop Loss : StopPrice :: " + stopPrice);
		postOrderToBinance(OrderSide.SELL, OrderType.STOP_LOSS, null, null, String.valueOf(stopPrice));

		TimeUnit time = TimeUnit.SECONDS;
		System.out.print("\n Step 4 : Wait for 20 seconds");
		try {
			time.sleep(20);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.print("\n Step 5 : Placing Sell order : Price :: " + sellPriceDrop);
		postOrderToBinance(OrderSide.SELL, OrderType.LIMIT, TimeInForce.GTC, String.valueOf(sellPriceDrop), null);

	}

}
