package com.enigmadevelopment.economymod;

//TODO: MAKE THIS CLASS SERIALIZABLE

import java.io.Serializable;

public class SQLConnectionTemplate implements Serializable {
    private String url;
    private String port;
    private String username;
    private String password;
    private String database;
    private String balanceTableName;
    private String priceTableName;


    public SQLConnectionTemplate(String url, String port, String username, String password, String database, String balanceTableName, String priceTableName) {
        this.url = url;
        this.port = port;
        this.username = username;
        this.password = password;
        this.database = database;
        this.balanceTableName = balanceTableName;
        this.priceTableName = priceTableName;
    }
    /*
    @param MySQL URL
    @param MySQL Port
    @param MySQL username
    @param MySQL password
    @param database name
    @param table name of user balances
    @param table name of item values
    */

    public String getDatabase() {
        return database;
    }

    public String getUrl() {
        return this.url;
    }

    public String getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }


    public String getPassword() {
        return password;
    }


    public String getBalanceTableName() {
        return balanceTableName;
    }

    public String getPriceTableName() {
        return priceTableName;
    }

}
//This object contains the necessary information to connect to the MySQL database and get
//user balances and item values from it.
