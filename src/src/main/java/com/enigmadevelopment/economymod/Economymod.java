package com.enigmadevelopment.economymod;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class Economymod extends JavaPlugin implements Listener {
    private MySQL mySQL;
    //Object representing the MySQL system.  Used for all database queries and updates.

    private String balanceTableName;
    //The name of the MySQL table that player balances are in.

    private String itemValuesTableName;
    //The name of the MySQL table that item values are in.

    private HashMap<String, Integer> balances = new HashMap<>();
    private HashMap<String, Integer[]> itemValues = new HashMap<>();
    //Local caches to store what the database has for player balances and item values.  itemValues.get()[0] is cost to buy and [1] is value when selling

    private ArrayList<Player> waitingToBeProcessed = new ArrayList<>();
    //Holds players that joined while the application was setting player balances.  Ensures nobody is left out.

    private boolean settingOnlinePlayerBalances = false;
    //Add players to waitingToBeProccessed (true) or process them immediately (false)?

    @Override
    public void onEnable() {
        printToLogger("Economy mod initialization started!");
        getServer().getPluginManager().registerEvents(this, this);
        //Registers this object as an event handler (necessary for handling the player login event)

        Path potentialDatabaseTemplateLocation = Paths.get("/home/minecraft/server/plugins/EconomyModData", "DatabaseConfig.txt");
        //Where the database configuration file will be if there is one

        if (!Files.exists(potentialDatabaseTemplateLocation)) {
            printToLogger("Use the following command to set up your server:" +
                    "/economymodSQLSETUP $url $port $username $password $database $balanceTableName $priceTableName");
        } else {
            SQLConnectionTemplate connectionTemplate;
            try {
                connectionTemplate = SQLSerializer.deserializeSQL(potentialDatabaseTemplateLocation);
            } catch (IOException | ClassNotFoundException e) {
                printToLogger("An error occoured while reading SQL setup data from file.  You are not connected to the database.");
                printStackTraceToLogger(e.getStackTrace());
                return;
            }
            setupMySQLConnection(connectionTemplate);
        }
        //If there is a file there, call the SQL setup method with an object form of that file and a reference to this object and handle any exceptions.  Else, print
        //to the admin console that setup needs to be done.
    }

    private boolean handleSQLSetupCommand(String[] args) {
        if (!verifyArgsLength(6, args)) {return false;}
        String url = args[0];
        String port = args[1];
        String username = args[2];
        String password = args[3];
        String database = args[4];
        String balanceTableName = args[5];
        String itemValuesTableName = args[6];
        SQLConnectionTemplate sqlConnectionTemplate = new SQLConnectionTemplate(url, port, username, password, database, balanceTableName, itemValuesTableName);
        //Creates an SQLConnectionTemplate out of the information that the admin input through the command.

        Path filePath;
        try {
            new File("/home/minecraft/server/plugins/EconomyModData").mkdirs();
            filePath = Paths.get("/home/minecraft/server/plugins/EconomyModData", "DatabaseConfig.txt");
            SQLSerializer.serializeSQL(sqlConnectionTemplate, filePath);
        } catch (IOException e) {
            printToLogger("An error occurred in handleSQLSetupCommand.  Message:\n" + e.getMessage() + "\n");
            printStackTraceToLogger(e.getStackTrace());
            return false;
        }
        //Serializes the SQLConnectionTemplate to disk and handles any errors that occour.

        return setupMySQLConnection(sqlConnectionTemplate);
        //Calls another method to connect to the database and fill this object's cache HashMaps
    }


    private boolean setupMySQLConnection(SQLConnectionTemplate connectionTemplate) {
        this.mySQL = new MySQL(connectionTemplate);
        //Create a MySQL object out of the information in the connectionTemplate.

        try {
            this.mySQL.openConnection();
        } catch (ClassNotFoundException | SQLException e) {
            printToLogger("An error has occoured while connecting to the database.  Message:\n" + e.getMessage());
            printStackTraceToLogger(e.getStackTrace());
            return false;
        }
        //Open the connection to the database on the MySQL object.  Handle any errors.

        this.balanceTableName = connectionTemplate.getBalanceTableName();
        this.itemValuesTableName = connectionTemplate.getPriceTableName();
        //Set the instance variables that hold the names of the tables for player balances and values of items.

        try {
            setBalances();
            setItemValuesMap();
        } catch (SQLException | ClassNotFoundException e) {
            printToLogger("An error occoured while setting up caches.  Try using economymodSQLSetup. Message: " + e.getMessage());
            printStackTraceToLogger(e.getStackTrace());
            this.mySQL = null;
            this.balanceTableName = this.itemValuesTableName = null;
            this.balances.clear();
            this.itemValues.clear();
            return false;
        }
        return true;
        //Set the local hashmaps that hold player balances and the values of items.  If this fails then clear the maps and undo everything this method did.
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @EventHandler
    public void onLogin(PlayerJoinEvent event) {
        if (balances.get(event.getPlayer().getDisplayName()) != null) {
        }
        else if (settingOnlinePlayerBalances) {
            waitingToBeProcessed.add(event.getPlayer());
        } else {
            addPlayerToBalances(event.getPlayer());
        }
    }
    //When a player logs in, see if they are already in the balances hashmap.  If they're not and balances are currently
    //being set, add them to a list of waiting to be added players.  If it's not currently being set, call addPlayerToBalances()
    //to add them to the map.

    @Override
    public void onDisable() {
        saveBalancesToDatabase();
        saveItemValuesToDatabase();
    }
    //Calls methods to save user balances and item prices to the MySQL Database.

    private void saveBalancesToDatabase() {
        for (Map.Entry<String, Integer> entry : balances.entrySet()) {
            try {
                mySQL.updateSQL("INSERT INTO " + balanceTableName + " (Username, Balance) VALUES (\"" + entry.getKey() + "\", " + entry.getValue() + ") ON DUPLICATE KEY UPDATE Balance=" + entry.getValue());
            } catch (SQLException | ClassNotFoundException e) {
                printToLogger("An sql exception has occurred.  Cause: " + e.getMessage() + "\n");
                printStackTraceToLogger(e.getStackTrace());
            }
        }
    }
    //This method is called by onDisable() to save user's balances from this object's balances hashmap to the MySQL database.

    private void saveItemValuesToDatabase() {
        for (Map.Entry<String, Integer[]> entry : itemValues.entrySet()) {
            try {
                mySQL.updateSQL("INSERT INTO " + this.itemValuesTableName + " (ID, BuyValue, SellValue) VALUES (\"" + entry.getKey() + "\", " + entry.getValue()[0] + ", "
                        + entry.getValue()[1] + ") ON DUPLICATE KEY UPDATE BuyValue=" + entry.getValue()[0] + ", SellValue=" + entry.getValue()[1]);
            } catch (SQLException | ClassNotFoundException e) {
                printToLogger("An exception occurred while saving the item value cache to the database.  Message: " + e.getMessage());
                printStackTraceToLogger(e.getStackTrace());
            }
        }
    }
    //This method is called by onDisable() to save the values of items from this object's  itemValues hashmap to the MySQL database.

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            return handlePlayerCommand((Player) sender, command, args);
        } else {
            return handleConsoleCommand(command, args);
        }
    }
    //This is the main command handler.  Sends the command off to separate methods depending on whether a player sent it or
    //the admin console

    private boolean handlePlayerCommand(Player sender, Command command, String[] args) {
        if (command.getName().contains("buy") || command.getName().contains("sell")) {
            return handleTransaction(sender, command, args);
        } else if (command.getName().equalsIgnoreCase("balance")) {
            if (balances.get(sender.getName()) == null) {
                printToLogger("Error occurred getting the balance of " + sender.getName());
                sender.sendMessage("An error has occurred while getting your balance.  The administrator has been notified.");
                return false;
            } else {
                sender.sendMessage("Your balance is " + balances.get(sender.getName()));
                return true;
            }
        } else {
            return false;
        }
    }
    //If it is a buy or sell command, send the command to handleTransaction().  Else if it is asking for user's balance,
    //return it from the balances hashmap and handle any errors if they exists.

    private boolean handleConsoleCommand( Command command, String[] args) {
        if (command.getName().equalsIgnoreCase("economymodsqlsetup")) {
            if (handleSQLSetupCommand(args)) {
                return true;
            } else {
                return false;
            }
        } else if (command.getName().equalsIgnoreCase("getbalance")) {
            if (!verifyArgsLength(1, args)) {return false;}
            if (balances.get(args[0]) == null) {
                printToLogger("User not found!");
                return false;
            }
            printToLogger(args[0] + "'s balance is " + balances.get(args[0]));
            return true;
        } else if (command.getName().equalsIgnoreCase("setbalance")) {
            if (!verifyArgsLength(2, args)) {return false;}
            int balanceToSetTo;
            try {
                balanceToSetTo = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                printToLogger("You input an invalid balance to set to!");
                return false;
            }
            if (balances.get(args[0]) == null) {
                printToLogger("User not found!");
                return false;
            }
            balances.put(args[0], balanceToSetTo);
            return true;
        } else if (command.getName().equalsIgnoreCase("setPrices")) {
            return setItemValue(args[0], args[1], args[2]);
        }
        else {
            return false;
        }
        //TODO: break this up into methods?
    }

    private void setBalances() throws SQLException, ClassNotFoundException {
        ResultSet resultSet;
        resultSet = this.mySQL.querySQL("SELECT Balance, Username FROM " + this.balanceTableName);
        while (resultSet.next()) {
            balances.put(resultSet.getString("Username"), resultSet.getInt("Balance"));
        }
        settingOnlinePlayerBalances = true;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (balances.get(p.getDisplayName()) == null) {
                addPlayerToBalances(p);
            }
        }
        for (Player p : waitingToBeProcessed) {
            if (balances.get(p.getDisplayName()) == null) {
                addPlayerToBalances(p);
            }
        }
        settingOnlinePlayerBalances = false;
        waitingToBeProcessed.clear();
        settingOnlinePlayerBalances = false;
        printToLogger("I've successfully gotten user balances from the database.");
    }
    //This method loads the balances of all players in the MySQL database, then adds players who are online and were
    //not in the database, and finally players who joined while the previous two actions were being executed.

    private void addPlayerToBalances(Player player) {
        balances.put(player.getDisplayName(), 0);
        printToLogger(player.getDisplayName() + " has been added to balances.");
    }
    //This method adds @param player to the HashMap balances.  Used by setBalances() and onLogin().

    private void setItemValuesMap() throws SQLException, ClassNotFoundException {
            ResultSet rs = this.mySQL.querySQL("SELECT ID, BuyValue, SellValue FROM " + this.itemValuesTableName);
            while (rs.next()) {
                itemValues.put(rs.getString("ID"), new Integer[] {rs.getInt("BuyValue"), rs.getInt("SellValue")});
            }
    }
    //This method sets the item value cache by retrieving the buying and selling price from the databse.
    //They are stored in HashMap<String, int[]> itemValues with value[0] being the cost to buy
    //them and value[1] being the value if they are sold.

    private boolean setItemValue(String itemID, String buyValue, String sellValue) {
        int buyValueInt;
        int sellValueInt;
        try {
            buyValueInt = Integer.parseInt(buyValue);
            sellValueInt = Integer.parseInt(sellValue);
        } catch (NumberFormatException e) {
            printToLogger("You input an invalid number!  Exception message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        itemValues.put(itemID, new Integer[] {buyValueInt, sellValueInt});
        return true;
    }
    //This admin-console command handler sets the price of a specific item.  /setItemValue name int1 int2 sets the buying
    //cost of name to int1 and the selling value to int2 in the itemValue HashMap.

    private boolean handleTransaction(Player sender, Command command, String[] args) {
        if (!verifyArgsLength(2, args)) {return false;}
        //Verify proper number of args were passed in

        String transactionType = (command.getName().equalsIgnoreCase("buy") ? "BUY" : "SELL");
        //Store the type of transaction

        int numToTransact;
        try {
            numToTransact = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            sender.sendMessage("That is not a valid number!");
            return false;
        }
        if (numToTransact <= 0) {
            sender.sendMessage("That is not a valid number!");
            return false;
        }
        //Gets the number of items to transact and handles invalid-input errors.

        String item = args[1].toUpperCase();
        Material itemAsMaterial;
        try {
            itemAsMaterial = Material.valueOf(item);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("You tried to transact an invalid item!");
            return false;
        }
        //Get the type of item to transact as a Material enum.  Catch invalid-input errors.

        int price = (transactionType.equals("BUY") ? itemValues.get(item)[0] : itemValues.get(item)[1]);
        //Get the value of the item with respect to the transaction type.

        if (transactionType.equals("SELL")) {
            if (sender.getInventory().contains(itemAsMaterial, numToTransact)) {
                sender.getInventory().removeItem(new ItemStack(itemAsMaterial, numToTransact));
                balances.put(sender.getDisplayName(), (balances.get(sender.getDisplayName())+(numToTransact * price)));
                sender.sendMessage("You successfully sold " + numToTransact + " " + item + "'s!");
                return true;
            } else {
                sender.sendMessage("You tried to sell more than you had!");
                return false;
            }
            //If the player has enough of that item to sell, remove that may items from their invintory and
            //add (sold * sellValue) to their balance in the local cache.  Else, return an error.
        } else {
            if (balances.get(sender.getDisplayName()) < (price * numToTransact)) {
                sender.sendMessage("You can't afford that many items!");
                return false;
            }
            balances.put(sender.getDisplayName(), (balances.get(sender.getDisplayName())-(numToTransact * price)));
            sender.getInventory().addItem(new ItemStack(itemAsMaterial, numToTransact));
            sender.sendMessage("You successfully bought " + numToTransact + " " + item + "'s!");
            return true;
            //If they don't have enough balance to cover (buyPrice * numToBuy), throw an error.  Else, subtract that
            //from their balance in the local cache and add that many items to their invintory.
        }
    }

    private boolean verifyArgsLength(int length, String[] args) {
        if (args.length == length) {
            return true;}
        else {
            printToLogger("Incorrect Command Usage");
            return false;
        }
    }
    //This helper method verifies that an args array created by a command contains the correct number of words.
    //If it returns false, meaning it was the incorrect number of args, the command handler will return without
    //executing

    void printStackTraceToLogger(StackTraceElement[] traceAr) {
        for (StackTraceElement ste : traceAr) {
            printToLogger(ste.toString());
        }
    }
    //This helper method prints Exception e's e.getStackTrace() to the
    //admin console

    void printToLogger(String toPrint) {
        getLogger().info(toPrint);
    }
    //This helper method prints an input string to the admin console.  Used for all output to admin.
}