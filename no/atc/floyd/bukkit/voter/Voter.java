package no.atc.floyd.bukkit.voter;




import java.io.*;


import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;


import java.util.regex.*;
import java.sql.*;

/**
* Approve plugin for Bukkit
*
* @author FloydATC
*/
public class Voter extends JavaPlugin implements Listener {
    
    private final ConcurrentHashMap<Player, Boolean> debugees = new ConcurrentHashMap<Player, Boolean>();
    public final ConcurrentHashMap<String, String> settings = new ConcurrentHashMap<String, String>();

    public static DbPool dbpool = null;
    
    String baseDir = "plugins/Voter";
    String configFile = "settings.txt";

	public static final Logger logger = Logger.getLogger("Minecraft.Voter");
    
//    public Voter(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
//        super(pluginLoader, instance, desc, folder, plugin, cLoader);
//        // TODO: Place any custom initialization code here
//
//        // NOTE: Event registration should be done in onEnable not here as all events are unregistered when a plugin is disabled
//    }

    public void onDisable() {
        // TODO: Place any custom disable code here
    	
        // NOTE: All registered events are automatically unregistered when a plugin is disabled
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
    	PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is disabled!" );
    }

    public void onEnable() {
        // TODO: Place any custom enable code here including the registration of any events

    	loadSettings();
    	initDbPool();
    	
        // EXAMPLE: Custom code, here we just output some info so we can check all is well
        PluginDescriptionFile pdfFile = this.getDescription();
		logger.info( pdfFile.getName() + " version " + pdfFile.getVersion() + " is enabled!" );
    }

    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args ) {
    	String cmdname = cmd.getName().toLowerCase();
        Player player = null;
        String pname = "(Console)";
        if (sender instanceof Player) {
        	player = (Player)sender;
        	pname = player.getName();
        }
        Connection dbh = null;
        
        if (cmdname.equalsIgnoreCase("vote")) {
        	
        	// Reload
    		if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
    			if (player == null || player.isOp() || player.hasPermission("voter.reload")) {
        			respond(player, "§7[§6Vote§7]§a Reloading configuration file");
        			loadSettings();
        			initDbPool();
        			return true;
        		} else {
        			logger.info("[Vote] "+pname+" tried to reload but does not have permission");
        			return false;
        		}
        	}
        	
        	// Help
    		if (args.length == 3 && args[0].equalsIgnoreCase("op")) {
    			
	        	if (player != null && player.hasPermission("voter.vote")) {
	        		if (dbpool == null) {
	        			logger.info("[Vote] Retrying dbpool initialization...");
	        			initDbPool();
	        		}
	       	        if (dbpool != null) { 
	       	        	dbh = dbpool.getConnection();
	       	        	if (dbh != null) {
	       	        		String cname = args[1];
	       	        		Player candidate = getServer().getPlayer(cname);
	       	        		String vote = args[2];
	       	        		if (candidate != null) {
	       	        			cname = candidate.getName();
	       	        		}
	       	        		if (pname.equalsIgnoreCase(cname)) {
	       	        			respond(player, "§7[§6Vote§7]§c You don't VOTE for kings!");
	       	        			return true;
	       	        			
	       	        		}
	
	       	        		// Vote yes?
	       	        		if (vote.equalsIgnoreCase("yes") || vote.equalsIgnoreCase("yay")) {
	       	        			if (voteYes(dbh, pname, cname)) {
	       	        				respond(player, "§7[§6Vote§7]§a Registered YES vote for §6"+cname);
	       	        			} else {
	       	        				respond(player, "§7[§6Vote§7]§c There was an unexpected problem, please try again");
	       	        			}
	           	        		dbpool.releaseConnection(dbh);
	       	        			return true;
	       	        		}
	
	       	        		// Vote no?
	       	        		if (vote.equalsIgnoreCase("no") || vote.equalsIgnoreCase("nay")) {
	       	        			if (voteNo(dbh, pname, cname)) {
	       	        				respond(player, "§7[§6Vote§7]§a Registered NO vote for §6"+cname);
	       	        			} else {
	       	        				respond(player, "§7[§6Vote§7]§c There was an unexpected problem, please try again");
	       	        			}
	           	        		dbpool.releaseConnection(dbh);
	       	        			return true;
	       	        		}
	
	       	        		// Cancel vote?
	       	        		if (vote.equalsIgnoreCase("cancel") || vote.equalsIgnoreCase("ignore")) {
	       	        			if (voteCancel(dbh, pname, cname)) {
	       	        				respond(player, "§7[§6Vote§7]§a Cancelled vote for §6"+cname);
	       	        			} else {
	       	        				respond(player, "§7[§6Vote§7]§c There was an unexpected problem, please try again");
	       	        			}
	           	        		dbpool.releaseConnection(dbh);
	       	        			return true;
	       	        		}
	       	        		
	       	        		dbpool.releaseConnection(dbh);
	       	        	} else {
	                		respond(player, "§7[§6Vote§7]§c Database not responding, please try again later.");
	                		logger.warning("[Vote] Database not responding");       	        	
	                	}
	       	        } else {
	            		respond(player, "§7[§6Vote§7]§c Database not available, please try again later.");
	            		logger.warning("[Vote] Database not available");       	        	
	            	}
	        		return true;
	        	} else {
	        		logger.info("[Vote] "+pname+" tried to vote but does not have permission");
	        	}
        	}
        }

        return false;
    }

    private void initDbPool() {
    	try {
	    	dbpool = new DbPool(
	    		settings.get("db_url"), 
	    		settings.get("db_user"), 
	    		settings.get("db_pass"),
	    		Integer.valueOf(settings.get("db_min")),
	    		Integer.valueOf(settings.get("db_max"))
	    	);
    	} catch (RuntimeException e) {
    		logger.warning("[Vote] Init error: "+e.getLocalizedMessage());
    	}
    }
    
    
    
    public boolean isDebugging(final Player player) {
        if (debugees.containsKey(player)) {
            return debugees.get(player);
        } else {
            return false;
        }
    }

    public void setDebugging(final Player player, final boolean value) {
        debugees.put(player, value);
    }
    
    // Code from author of Permissions.jar
    
    private void loadSettings() {
    	String fname = baseDir + "/" + configFile;
		String line = null;

		// Load the settings hash with defaults
		settings.put("db_url", "");
		settings.put("db_user", "");
		settings.put("db_pass", "");
		settings.put("db_min", "2");
		settings.put("db_max", "10");
		settings.put("yes_query", "REPLACE INTO opvotes (voter, candidate, vote) VALUES (?,?,1)");
		settings.put("no_query", "REPLACE INTO opvotes (voter, candidate, vote) VALUES (?,?,-1)");
		settings.put("cancel_query", "DELETE FROM opvotes WHERE voter=? AND candidate=?");
		// Read the current file (if it exists)
		try {
    		BufferedReader input =  new BufferedReader(new FileReader(fname));
    		while (( line = input.readLine()) != null) {
    			line = line.trim();
    			if (!line.startsWith("#") && line.contains("=")) {
    				String[] pair = line.split("=", 2);
    				settings.put(pair[0], pair[1]);
    			}
    		}
    	}
    	catch (FileNotFoundException e) {
			logger.warning( "[Vote] Error reading " + e.getLocalizedMessage() + ", using defaults" );
    	}
    	catch (Exception e) {
    		e.printStackTrace();
    	}
    }
    
    
    private void respond(Player player, String message) {
    	if (player == null) {
        	Server server = getServer();
        	ConsoleCommandSender console = server.getConsoleSender();
        	console.sendMessage(message);
    	} else {
    		player.sendMessage(message);
    	}
    }
    

    private boolean voteYes(Connection dbh, String pname, String cname) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(settings.get("yes_query"));
   			sth.setNString(1, pname);
   			sth.setNString(2, cname);
   			sth.executeUpdate();
   			logger.info("[Vote] "+pname+" voted YES for "+cname);
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning("[Vote] SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }
    
    private boolean voteNo(Connection dbh, String pname, String cname) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(settings.get("no_query"));
   			sth.setNString(1, pname);
   			sth.setNString(2, cname);
   			sth.executeUpdate();
   			logger.info("[Vote] "+pname+" voted NO for "+cname);
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning("[Vote] SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }

    private boolean voteCancel(Connection dbh, String pname, String cname) {
		try {
	        PreparedStatement sth;
	        sth = dbh.prepareStatement(settings.get("cancel_query"));
   			sth.setNString(1, pname);
   			sth.setNString(2, cname);
   			sth.executeUpdate();
   			logger.info("[Vote] "+pname+" cancelled vote for "+cname);
		} catch (SQLException e) {
			e.printStackTrace();
			logger.warning("[Vote] SQL error: "+e.getLocalizedMessage());
			return false;
		}
    	return true;
    }

}

