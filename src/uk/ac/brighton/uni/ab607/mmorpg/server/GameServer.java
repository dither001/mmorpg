package uk.ac.brighton.uni.ab607.mmorpg.server;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import uk.ac.brighton.uni.ab607.libs.main.Out;
import uk.ac.brighton.uni.ab607.libs.net.*;
import uk.ac.brighton.uni.ab607.mmorpg.client.ui.Animation;
import uk.ac.brighton.uni.ab607.mmorpg.common.*;
import uk.ac.brighton.uni.ab607.mmorpg.common.Enemy.EnemyType;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.AgentBehaviour;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.AgentGoal;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.AgentGoalTarget;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.AgentRule;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.AgentType;
import uk.ac.brighton.uni.ab607.mmorpg.common.ai.EnemyAgent;
import uk.ac.brighton.uni.ab607.mmorpg.common.combat.Element;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.Armor;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.ArmorFactory;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.Chest;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.EquippableItem;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.GameItem;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.Weapon;
import uk.ac.brighton.uni.ab607.mmorpg.common.item.WeaponFactory;

class Point implements java.io.Serializable, AgentGoalTarget {
    /**
     *
     */
    private static final long serialVersionUID = 5721555806534123308L;
    private int x, y;
    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }
    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }
}

public class GameServer {

    public enum Command {
        ATTR_UP, EQUIP, UNEQUIP, REFINE;

        @Override
        public String toString() {
            return this.name();
        }
    }

    public static final String ATTR_UP = "ATTR_UP",
            EQUIP = "EQUIP",
            UNEQUIP = "UNEQUIP",
            REFINE = "REFINE";

    private static final int ATK_INTERVAL = 50;
    private static final int ENEMY_SIGHT = 320;

    private UDPServer server = null;

    private ArrayList<Player> players = new ArrayList<Player>();
    private ArrayList<Chest> chests = new ArrayList<Chest>();
    private ArrayList<Enemy> enemies = new ArrayList<Enemy>();

    private ArrayList<Animation> animations = new ArrayList<Animation>();

    private ArrayList<AgentRule> aiRules = new ArrayList<AgentRule>();
    //private ArrayList<Player> facts = new ArrayList<Player>();
    private HashMap<Point, Float> locationFacts = new HashMap<Point, Float>();

    public GameServer() {
        try {
            server = new UDPServer(55555, new ClientQueryParser());
        }
        catch (SocketException e) {
            e.printStackTrace();
        }

        // TODO how to send maps to players i.e. where players are, map specs?

        chests.add(new Chest(80, 80, 1000, WeaponFactory.getWeaponById("4003"), WeaponFactory.getWeaponById("4001")));
        chests.add(new Chest(0, 80, 2033, ArmorFactory.getArmorById("5004"), ArmorFactory.getArmorById("5003")));

        enemies.add(new Enemy("Orc Warrior", "Test Mob", EnemyType.NORMAL, new AgentBehaviour(AgentType.GUARD, chests.get(0)), Element.NEUTRAL, 5, 640, 160));
        enemies.add(new Enemy("Orc Scout", "Test Mob", EnemyType.NORMAL, new AgentBehaviour(AgentType.SCOUT, null), Element.NEUTRAL, 5, 640, 640));
        enemies.add(new Enemy("Orc Scout2", "Test Mob", EnemyType.NORMAL, new AgentBehaviour(AgentType.SCOUT, null), Element.NEUTRAL, 5, 1280, 1200));
        enemies.add(new Enemy("Elven Mercenary", "Test Mob", EnemyType.NORMAL, new AgentBehaviour(AgentType.ASSASSIN, null), Element.NEUTRAL, 5, 720, 720));

        // AI RULES

        AgentRule rule = new AgentRule(AgentType.GUARD, AgentGoal.GUARD_CHEST) {
            @Override
            public void execute(EnemyAgent agent, AgentGoalTarget target) {
                //Out.debug("Called within execute");


                if (target != null)
                    agent.patrol(target);
            }
        };

        AgentRule rule2 = new AgentRule(AgentType.GUARD, AgentGoal.KILL_PLAYER) {
            @Override
            public void execute(EnemyAgent agent, AgentGoalTarget target) {
                List<Player> tmpPlayers = new ArrayList<Player>(players);
                for (Player p : tmpPlayers) {
                    if (agent.canSee(p)) {
                        target = p;
                        break;
                    }
                }

                if (target != null)
                    agent.attack(target);
            }
        };

        AgentRule rule3 = new AgentRule(AgentType.SCOUT, AgentGoal.FIND_PLAYER) {
            @Override
            public void execute(EnemyAgent agent, AgentGoalTarget target) {
                /*if (facts.size() > 0) {
                    List<Player> tmpPlayers = new ArrayList<Player>(players);
                    agent.search(tmpPlayers.get(0));
                }
                else {*/
                agent.search(getLastKnownLocation());
                // }

                //agent.search(null);
            }
        };

        AgentRule rule4 = new AgentRule(AgentType.SCOUT, AgentGoal.KILL_PLAYER) {
            @Override
            public void execute(EnemyAgent agent, AgentGoalTarget target) {
                if (target != null)
                    agent.attack(target);
            }
        };

        AgentRule rule5 = new AgentRule(AgentType.ASSASSIN, AgentGoal.KILL_PLAYER) {
            @Override
            public void execute(EnemyAgent agent, AgentGoalTarget target) {
                List<Player> tmpPlayers = new ArrayList<Player>(players);
                for (Player p : tmpPlayers) {
                    if (agent.canSee(p)) {
                        target = p;
                        break;
                    }
                }

                if (target != null)
                    agent.attack(target);
            }
        };

        // TODO: add more rules

        aiRules.add(rule);
        aiRules.add(rule2);
        aiRules.add(rule3);
        aiRules.add(rule4);
        aiRules.add(rule5);




        // start main server loop
        new Thread(new ServerLoop()).start();
    }

    class ClientQueryParser extends ClientPacketParser {
        @Override
        public void parseClientPacket(DataPacket packet) {
            if (packet.objectData instanceof Player) {
                Player clientPlayer = (Player) packet.objectData;

                boolean newPlayer = true;
                // do we need this?
                for (int i = 0; i < players.size(); i++) {
                    if (players.get(i).name.equals(clientPlayer.name)) {
                        newPlayer = false;
                        players.set(i, clientPlayer);
                        break;
                    }
                }

                if (newPlayer) {
                    players.add(clientPlayer);
                    Out.println(clientPlayer.name + " joined the game");
                }
            }

            if (packet.stringData.startsWith("CHECK_PLAYER")) {
                String[] data = new String(packet.byteData).split(","); // TODO: exception check

                String user = data[0];
                String pass = data[1];

                if (GameAccount.getAccountByUserName(user) == null) {
                    GameAccount.addAccount(user, pass, "test@mail.com");    // created new account
                    try {
                        server.send(new DataPacket("New Account Created"), packet.getIP(), packet.getPort());
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                String response = "";

                if (!playerNameExists(user) && GameAccount.validateLogin(user, pass)) {
                    response = "CHECK_PLAYER_GOOD";
                }
                else {
                    response = "CHECK_PLAYER_BAD";
                }

                try {
                    server.send(new DataPacket(response), packet.getIP(), packet.getPort());
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (packet.stringData.startsWith("CLOSE")) {
                closePlayerConnection(packet.stringData.split(",")[1]); //TODO: ex check
            }

            if (packet.multipleObjectData instanceof String[]) {
                try {
                    parseActions((String[]) packet.multipleObjectData);
                }
                catch (BadActionRequestException e) {
                    Out.err(e);
                }
            }
        }

        /**
         *
         * @param name
         *              player name
         * @return
         *          true if player name exists on server, false otherwise
         */
        private boolean playerNameExists(String name) {
            for (Player p : players)
                if (p.name.equals(name))
                    return true;

            return false;
        }

        /**
         * No longer tracks the player but client
         * still receives updates at this point
         *
         * TODO: don't send updates after method finishes
         * @param playerName
         *                  name of the player to disconnect
         */
        private void closePlayerConnection(String playerName) {
            for (Iterator<Player> iter = players.iterator(); iter.hasNext(); ) {
                if (iter.next().name.equals(playerName)) {
                    iter.remove();
                    break;
                }
            }
        }
    }

    // TODO: do we process skill usage here ?
    private void parseActions(String[] actions) throws BadActionRequestException {
        for (String action : actions) {
            String[] tokens = action.split(",");
            // [0] - cmd, [1] - player name, [2] - cmd associated value
            // TODO: do we specify command length? or simply check with ifs and choose appropriate cmd

            String cmd = tokens[0];
            Player player = getPlayerByName(tokens[1]);
            if (player == null)
                throw new BadActionRequestException("No player name found: " + tokens[1]);

            int value = 0;
            try {
                value = Integer.parseInt(tokens[2]);
            }
            catch (NumberFormatException e) {
                throw new BadActionRequestException("Bad value: " + tokens[2]);
            }

            switch (cmd) {
                case ATTR_UP:
                    player.increaseAttr(value);
                    break;
                case EQUIP:
                    GameItem item = player.getInventory().getItem(value);
                    if (item != null) {
                        if (item instanceof Weapon) {
                            player.equipWeapon((Weapon) item);
                        }
                        else if (item instanceof Armor) {
                            player.equipArmor((Armor) item);
                        }
                        else
                            throw new BadActionRequestException("Item not equippable: " + value);
                    }
                    else
                        throw new BadActionRequestException("Item not found: " + value);
                    break;
                case UNEQUIP:
                    player.unEquipItem(value);
                    break;
                case REFINE:
                    GameItem itemToRefine = player.getInventory().getItem(value);

                    if (itemToRefine != null) {
                        if (itemToRefine instanceof EquippableItem) {
                            ((EquippableItem) itemToRefine).refine();
                        }
                        else
                            throw new BadActionRequestException("Item cannot be refined: " + value);
                    }
                    else
                        throw new BadActionRequestException("Item not found: " + value);
                    break;
                default:
                    throw new BadActionRequestException("No such command: " + tokens[0]);
            }
        }
    }

    private Player getPlayerByName(String name) {
        for (Player p : players)
            if (p.name.equals(name))
                return p;

        return null;
    }

    class ServerLoop implements Runnable {
        @Override
        public void run() {
            List<Player> tmpPlayers = new ArrayList<Player>();

            while (true) {

                tmpPlayers = new ArrayList<Player>(players);

                for (Iterator<Animation> itA = animations.iterator(); itA.hasNext(); ) {
                    Animation a = itA.next();
                    a.duration -= 20.0f / 1000.0f;
                    if (a.duration <= 0)
                        itA.remove();
                }


                // process AI
                for (Enemy e : enemies) {
                    AgentBehaviour ai = e.AI;
                    for (AgentRule rule : aiRules) {
                        // TODO add to list
                        if (rule.matches(ai.type, ai.currentGoal)) {
                            //Out.debug("Called execute");
                            // disable AI
                            //rule.execute(e, ai.currentTarget);
                        }
                    }
                }

                //Out.debug(players.size() + "");

                // move players
                for (Player p : tmpPlayers) {
                    if (locationFacts.size() == 0) {
                        locationFacts.put(new Point(p.getX(), p.getY()), 0.1f);
                    }

                    p.move();
                    p.xSpeed = 0;
                    p.ySpeed = 0;
                    for (Enemy e : enemies) {
                        if (e.AI.currentGoal == AgentGoal.GUARD_CHEST
                                && p.getX() == e.AI.currentTarget.getX()
                                && p.getY() == e.AI.currentTarget.getY()) {
                            e.AI.setGoal(AgentGoal.KILL_PLAYER);
                            e.AI.setTarget(p);
                        }

                        if (e.AI.currentGoal == AgentGoal.FIND_PLAYER && e.canSee(p)) {
                            locationFacts.put(new Point(p.getX(), p.getY()), 1.0f);



                            //if (!facts.contains(p))
                            //facts.add(p);
                            e.AI.currentTarget = p;
                        }

                        if (e.AI.currentGoal == AgentGoal.KILL_PLAYER && e.AI.currentTarget == null
                                && locationFacts.size() > 0) {

                            e.AI.currentTarget = getLastKnownLocation();

                            //Out.debug(e.AI.currentTarget.getX() + " " + e.AI.currentTarget.getY());
                        }

                        //if (e.AI.currentGoal == AgentGoal.KILL_PLAYER && e.AI.currentTarget != null)

                    }
                }

                // fuzzy stuff
                Iterator<Entry<Point, Float>> iter = locationFacts.entrySet().iterator();
                while (iter.hasNext()) {
                    Map.Entry<Point, Float> pairs = (Map.Entry<Point, Float>)iter.next();
                    pairs.setValue((float) (pairs.getValue() - 0.005));
                    if (pairs.getValue() < 0)
                        iter.remove();

                    //System.out.println(pairs.getKey() + " = " + pairs.getValue());
                }

                //System.out.println(locationFacts.size() + "");

                // process player - chest interaction
                for (Player p : tmpPlayers) {   // make chests unavailable if picked and check for it (alive ? )
                    for (Chest c : chests) {
                        if (!c.isOpened() && p.getX() == c.x && p.getY() == c.y) {
                            c.open();
                            for (GameItem item : c.getItems())
                                p.getInventory().addItem(item);
                            p.getInventory().setChanged();
                            p.incMoney(c.money);
                        }
                    }
                }

                // process combat
                for (Player p : tmpPlayers) {
                    for (Enemy e : enemies) {
                        if (e.alive && e.getX() == p.getX() && e.getY() == p.getY()) {
                            if (++p.atkTime >= ATK_INTERVAL / (1 + p.getTotalStat(GameCharacter.ASPD)/100.0)) {
                                int dmg = p.dealDamage(e);
                                animations.add(new Animation(p.getX(), p.getY(), 0.5f, 0, 25, dmg+""));
                                p.atkTime = 0;
                                if (e.getHP() <= 0) {   // TODO: do similar checks when using skills
                                    p.gainBaseExperience(e.experience);
                                    p.gainJobExperience(e.experience);
                                    p.gainStatExperience(e.experience);
                                    chests.add(e.onDeath());
                                    //e.onDeath();    // TODO: check if OK, maybe pass player as who killed ?
                                }
                            }

                            if (++e.atkTime >= ATK_INTERVAL / (1 + e.getTotalStat(GameCharacter.ASPD)/100.0)) {
                                int dmg = e.dealDamage(p);
                                animations.add(new Animation(p.getX(), p.getY() + 80, 0.5f, 0, 25, dmg+""));
                                e.atkTime = 0;
                                if (p.getHP() <= 0) {
                                    /*p.xSpeed = -p.getX();
                                    p.ySpeed = -p.getY();
                                    p.move();
                                    p.xSpeed = 0;
                                    p.ySpeed = 0;*/
                                }

                            }
                        }
                    }
                }

                Player[] toSend = new Player[tmpPlayers.size()];
                for (int i = 0; i < tmpPlayers.size(); i++)
                    toSend[i] = tmpPlayers.get(i);

                // clean chests
                for (Iterator<Chest> it = chests.iterator(); it.hasNext(); ) {
                    if (it.next().isOpened()) {
                        it.remove();
                    }
                }

                Chest[] chestsToSend = new Chest[chests.size()];
                for (int i = 0; i < chests.size(); i++)
                    chestsToSend[i] = chests.get(i);

                Enemy[] eneToSend = new Enemy[enemies.size()];
                for (int i = 0; i < enemies.size(); i++)
                    eneToSend[i] = enemies.get(i);

                Animation[] animsToSend = new Animation[animations.size()];
                for (int i = 0; i < animations.size(); i++)
                    animsToSend[i] = animations.get(i);


                try {
                    server.send(new DataPacket(toSend));
                    server.send(new DataPacket(chestsToSend));
                    server.send(new DataPacket(eneToSend));
                    server.send(new DataPacket(animsToSend));
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    Thread.sleep(20);   // maybe even 10
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private AgentGoalTarget getLastKnownLocation() {
        AgentGoalTarget targ = null;
        float max = 0;
        for (Point p : locationFacts.keySet()) {
            if (locationFacts.get(p) > max) {
                max = locationFacts.get(p);
                targ = p;
            }
        }
        return targ;
    }


}
