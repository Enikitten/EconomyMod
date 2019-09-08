##Acknowledgments
Credit goes to tips48 from the Bukkit forums for creating the MySQL class.

##Pre-Requisites
This mod requires that you have SQLite set up on your server.

##Database Setup
Create a new SQLite database dedicated to this mod.  Create a new table within it that contains a string column named *Username* and an integer column named *Balance*.  Create another table with a string column named *ID*, an int column named *BuyValue*, and a int column named *SellValue*.  In the ID column, enter every Minecraft block ID that should be transact-able.  Enter how much the item should cost to buy in *BuyValue*, and how much it should be sold for for in *SellValue*.  The tables can be named anything.

##Initial setup
Copy economyMod.jar into your server’s main folder, usually found at */home/minecraft/server/plugins/* on Linux systems.  Reboot your server.  From the Bukkit command line, run the command */economymodSQLSETUP url port username password database balanceTableName priceTableName* where all arguments are the information for your SQLite database.

##Commands

*/buy numToBuy itemID – Buy numToBuy itemID’s*

*/sell numToSell itemID – Sell numToSell itemID’s*

*/getBalance – Return the current player’s balance*
