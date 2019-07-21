## Binance-Strategy-InfluxDB

   *This application is used to store the Order updates and the Price updates into the InfluxDB. This code handles all BTC pairs order updates and price updates. The stored data are visualized using Grafana tool.*
   
   *This application also create binance strategy from the local cached database and place the order for given atl-coin. Refer the strategy creation requirement file docs/strategy_requirement.docx*
   
### InfluxDB Setup

InfluxDB is a time series database designed to handle high write and query loads

   1. Download InfluxDB https://dl.influxdata.com/influxdb/releases/influxdb-1.7.7_windows_amd64.zip
   
   2. unzip influxdb-1.7.7_windows_amd64.zip
   
   3. Go into the folder.
   
   4. Run influxd (server)
   
   5. Run influx (client)

   Note: InfluxDB server runs with the default port http://127.0.0.1:8086


## Start Project

   1. Download or clone the project
   
   2. Run the below jar file by providing either BTC or BNB as command line argument. This jar file continuosly load the data from             Binance to Influx database
   
           java -jar binance-strategy\target\binance-influxdb.jar BTC
           
   3. Goto influx client and run the below queries to verify the orderbook updates and the price updates
   
          select count(*) from "stock_retention"."orderbook";
          
          select count(*) from "stock_retention"."pricehistory";
          
   4. Run the below jar file in another command prompt to place a strategy order. Added argument parser to get the input from                 command line
    
      Check the command line arguments:
      
          java -jar binance-strategy\target\binance-strategy.jar
            Missing required options: a, m
            usage: utility-name
             -a,--altcoin <arg>     BTC pair alt-coin eg: ETHBTC
             -k,--apikey <arg>      Binance API-Key
             -m,--runmode <arg>     Run mode either test/real
             -s,--secretkey <arg>   Binance Secret-Key
   
      Actual Run:
      
          java -jar binance-strategy\target\binance-strategy.jar -a ETHBTC -m test > output.txt
          
          Note: Attached output file under the docs folder docs/output.txt
          
  5. Verify Order placed request by running below query in influx client
  
         select * from "stock_retention"."order_request";
         
         select * from "stock_retention"."order_response";
         Note: This table will be populated when we run the jar file with the argument runmode as real (-m real)
         

### Grafana Tool Setup

Grafana is an open source visualization tool. It allows you to query, visualize, alert on and understand your metrics no matter where they are stored. 

   1. Download Grafana https://dl.grafana.com/oss/release/grafana-6.2.5.windows-amd64.zip
   
   2. Installation steps available in https://grafana.com/docs/installation/windows/ (Change the port to 8888)
    
   Note: Open URL http://127.0.0.1:8888 to check the grafana. 

### Integrating Grafana with InfluxDB

Using Grafana GUI

   1. Add data source as InfluxDB and give necessary information to configure
   
   2. Create a variable. This is used to create dynamic dashboard based on the variable value.
   
      1. Go the Dashboard setting and select variable menu
      
      2. Create a new vaiable
         Name : BTCPAIR
         Type : Query
         DataSource : InfluxDB
         Query : show measurements
         
   3. Create a dashboard 
   
        1. Choose Table template 
        
        2. Queries : Add Query for BIDS table

          SELECT "price", "quantity", "price"*"quantity"  as Total FROM "stock_retention".$BTCPAIRS WHERE ("category" = 'BIDS') AND $timeFilter 

        3. Visualization: Format the table
        
        4. General: Give panel name
        
        5. Save the Dashboard
        
   4. Repeat the above step #2 by adding new panel in the same dashboard. And in the Queries section add the below query for ASKS
          
          SELECT "price", "quantity", "price"*"quantity"  as Total FROM "stock_retention".$BTCPAIRS WHERE ("category" = 'ASKS') AND $timeFilter 
     
   *Note: Refer visualization screenshot added inside docs folder.*
