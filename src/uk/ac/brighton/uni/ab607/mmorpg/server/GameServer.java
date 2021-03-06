package uk.ac.brighton.uni.ab607.mmorpg.server;

import java.io.IOException;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.almasb.common.graphics.Color;
import com.almasb.common.net.ClientPacketParser;
import com.almasb.common.net.DataPacket;
import com.almasb.common.net.UDPServer;
import com.almasb.common.search.AStarLogic;
import com.almasb.common.search.AStarNode;
import com.almasb.common.util.Out;

import uk.ac.brighton.uni.ab607.mmorpg.common.*;
import uk.ac.brighton.uni.ab607.mmorpg.common.object.GameMap;
import uk.ac.brighton.uni.ab607.mmorpg.common.object.ID;
import uk.ac.brighton.uni.ab607.mmorpg.common.object.ObjectManager;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.ActionRequest;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.AnimationMessage;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.ImageAnimationMessage;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.QueryRequest;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.QueryRequest.Query;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.ServerResponse;
import uk.ac.brighton.uni.ab607.mmorpg.common.request.TextAnimationMessage;

public class GameServer {
    private UDPServer server = null;

    private int playerRuntimeID = 1000;

    private ArrayList<Player> players = new ArrayList<Player>();

    private ServerActionHandler actionHandler;

    private ArrayList<GameMap> maps = new ArrayList<GameMap>();

    public GameServer() throws SocketException {
        actionHandler = new ServerActionHandler(this);

        // init world
        initGameMaps();

        // init server connection
        server = new UDPServer(55555, new ClientQueryParser());

        // start main server loop
        //new Thread(new ServerLoop()).start();
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(this::serverLoop, 0, 20, TimeUnit.MILLISECONDS);

        // call save state to db every 5 mins
        new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(this::saveState, 5, 5, TimeUnit.MINUTES);
    }

    private interface QueryAction {
        public void execute(DataPacket packet, QueryRequest req) throws IOException;
    }

    private class ClientQueryParser extends ClientPacketParser {
        private HashMap<Query, QueryAction> actions = new HashMap<Query, QueryAction>();

        public ClientQueryParser() {
            actions.put(Query.CHECK,  this::actionCheck);
            actions.put(Query.LOGIN,  this::actionLogin);
            actions.put(Query.LOGOFF, this::actionLogoff);
        }

        @Override
        public void parseClientPacket(DataPacket packet) {
            if (packet.objectData instanceof QueryRequest) {
                try {
                    actions.getOrDefault(((QueryRequest)packet.objectData).query,
                            this::actionNone).execute(packet, (QueryRequest)packet.objectData);
                }
                catch (IOException e) {
                    Out.e("parseClientPacket", "Bad query request", this, e);
                }
            }

            // handle action requests from clients
            if (packet.multipleObjectData instanceof ActionRequest[]) {
                actionHandler.process((ActionRequest[]) packet.multipleObjectData);
            }
        }

        private void actionCheck(DataPacket packet, QueryRequest req) throws IOException {
            String user = req.value1;
            String pass = req.value2;

            if (!GameAccount.exists(user)) {
                GameAccount.addAccount(user, pass, "test@mail.com");    // created new account
                server.send(new DataPacket(new ServerResponse(Query.CHECK, false, "New Account Created", "")),
                        packet.getIP(), packet.getPort());

                return;
            }

            boolean ok = !playerNameExists(user) && GameAccount.validateLogin(user, pass);
            server.send(new DataPacket(new ServerResponse(Query.CHECK, ok,
                    ok ? "Login Accepted" : "Login Rejected", "")), packet.getIP(), packet.getPort());
        }

        private void actionLogin(DataPacket packet, QueryRequest req) throws IOException {
            String name = req.value1;
            // get data from game account
            if (GameAccount.exists(name)) {
                Player p = GameAccount.getPlayer(name);
                p.ip = packet.getIP();
                p.port = packet.getPort();
                String mapName = GameAccount.getMapName(name);

                server.send(new DataPacket(new ServerResponse(Query.LOGIN, true, "Login successful", mapName,
                        p.getX(), p.getY())), packet.getIP(), packet.getPort());
                server.send(new DataPacket(p), packet.getIP(), packet.getPort()); // send player so client can init

                loginPlayer(mapName, p);
            }
            else {

                // purely for local debugging when db/accounts.db has been deleted
                Out.d("actionLogin", "Account not found, using new");

                String nam = "Debug";

                GameAccount.addAccount(nam, "pass", "test@mail.com");
                Player p = GameAccount.getPlayer(nam);
                p.ip = packet.getIP();
                p.port = packet.getPort();
                String mapName = GameAccount.getMapName(nam);

                server.send(new DataPacket(new ServerResponse(Query.LOGIN, true, "Login successful", mapName,
                        p.getX(), p.getY())), packet.getIP(), packet.getPort());
                server.send(new DataPacket(p)); // send player for init

                loginPlayer(mapName, p);

                saveState();
            }
        }

        private void actionLogoff(DataPacket packet, QueryRequest req) {
            closePlayerConnection(req.value1);
        }

        private void actionNone(DataPacket packet, QueryRequest req) {
            Out.e("actionNone", "Invalid QueryRequest: " + req.query, this, null);
        }

        /**
         *
         * @param name
         *              player name
         * @return
         *          true if player name exists on server (is online), false otherwise
         */
        private boolean playerNameExists(String name) {
            return getPlayerByName(name) != null;
        }

        /**
         * No longer tracks the player
         *
         * (The address however still remains
         * in the address "book" of UDPServer)
         *
         * @param playerName
         *                  name of the player to disconnect
         */
        private void closePlayerConnection(String playerName) {
            Player p = null;
            for (GameMap m : maps) {
                p = m.getPlayerByName(playerName);
                if (p != null) {
                    players.remove(p);
                    m.removePlayer(p);
                    GameAccount.setPlayer(p, p.name);
                    GameAccount.setMapName(m.name, p.name);
                    DBAccess.saveDB();
                    break;
                }
            }
        }
    }

    private void serverLoop() {
        try {
            for (GameMap map : maps)
                map.update(server);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *
     * @param name
     *              player name
     * @return
     *          player if name exists on the server (is online), if not then null
     */
    /*package-private*/ Player getPlayerByName(String name) {
        Player p = null;
        for (GameMap m : maps) {
            p = m.getPlayerByName(name);
            if (p != null)
                return p;
        }

        return null;
    }

    /**
     *
     * @param id
     *           runtime ID of the character
     * @return
     *          character (player, enemy or NPC) associated with this ID
     *          or null if ID doesn't exist
     */
    /*package-private*/ GameCharacter getGameCharacterByRuntimeID(int id, String mapName) {
        return getMapByName(mapName).getEnemyByRuntimeID(id);
    }

    /*package-private*/ Player getPlayerByRuntimeID(int id, String mapName) {
        return getMapByName(mapName).getPlayerByRuntimeID(id);
    }

    /*package-private*/ GameMap getMapByName(String name) {
        for (GameMap m : maps)
            if (m.name.equals(name))
                return m;

        return null;
    }

    /*package-private*/ void moveObject(GameCharacter ch, String mapName, int x, int y) {
        x /= 40; y /= 40;

        GameMap m = getMapByName(mapName);

        if (x < 0 || x >= m.width || y < 0 || y >= m.height)
            return;

        AStarNode[][] grid = m.getGrid();

        AStarNode targetNode = grid[x][y];
        AStarNode startN = grid[ch.getX()/40][ch.getY() / 40];

        for (int i = 0; i < m.width; i++)
            for (int j = 0; j < m.height; j++)
                grid[i][j].setHCost(Math.abs(x - i) + Math.abs(y - j));


        ArrayList<AStarNode> busyNodes = new ArrayList<AStarNode>();
        // find "busy" nodes
        //for (Enemy e : enemies) {
        //busyNodes.add(new AStarNode(e.getX()/40, e.getY()/40, 0, 1));
        //}

        AStarNode[] busy = new AStarNode[busyNodes.size()];
        for (int i = 0; i < busyNodes.size(); i++)
            busy[i] = busyNodes.get(i);

        List<AStarNode> closed = new AStarLogic().getPath(grid, startN, targetNode, busy);

        //Out.d("moveObject", closed.size() + "");

        if (closed.size() > 0) {
            AStarNode n = closed.get(0);

            if (ch.getX() > n.getX() * 40)
                ch.xSpeed = -5;
            if (ch.getX() < n.getX() * 40)
                ch.xSpeed = 5;
            if (ch.getY() > n.getY() * 40)
                ch.ySpeed = -5;
            if (ch.getY() < n.getY() * 40)
                ch.ySpeed = 5;

            ch.move();

            ch.xSpeed = 0;
            ch.ySpeed = 0;
        }
    }

    private void loginPlayer(String mapName, Player p) {
        p.setRuntimeID(playerRuntimeID++);
        GameMap m = getMapByName(mapName);
        m.addPlayer(p);

        players.add(p);

        try {
            String data = "";
            for (Player player : players) {
                data += player.getRuntimeID() + "," + player.name + ";";
            }

            server.send(new DataPacket(data), p.ip, p.port);
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        Out.println(p.name + " has joined the game. RuntimeID: " + p.getRuntimeID()
                + " Map: " + m.name);
        //addAnimation(new TextAnimation(800, 800, "Press I to open inventory", Color.GOLD, 5.0f), m.name);
        //addAnimation(new TextAnimation(800, 830, "Press S to open stats/skills", Color.GOLD, 10.0f), m.name);
    }

    /*package-private*/ void addTextAnimation(TextAnimationMessage a, String mapName) {
        getMapByName(mapName).animationsText.add(a);
    }

    /*package-private*/ void addTextAnimation(ImageAnimationMessage a, String mapName) {
        getMapByName(mapName).animationsImage.add(a);
    }

    private void initGameMaps() {
        maps.add(ObjectManager.getMapByName("map1.txt"));
    }

    public void saveState() {
        for (GameMap m : maps) {
            for (Player p : m.getPlayers()) {
                GameAccount.setPlayer(p, p.name);
                GameAccount.setMapName(m.name, p.name);
            }
        }

        DBAccess.saveDB();
    }

    /**
     *
     * @param ch1
     *              character 1
     * @param ch2
     *              character 2
     * @return
     *          distance between 2 characters in number of cells
     */
    /*package-private*/ int distanceBetween(GameCharacter ch1, GameCharacter ch2) {
        return (Math.abs(ch1.getX() - ch2.getX()) + Math.abs(ch1.getY() - ch2.getY())) / 40;
    }
}
