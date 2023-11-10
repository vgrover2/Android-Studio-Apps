package com.example.lab3;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

//import static java.lang.Thread.sleep;

public class jdbcDatabase{

    private Connection connection;

    // For Amazon Postgresql
    // private final String host = "ssprojectinstance.csv2nbvvgbcb.us-east-2.rds.amazonaws.com"

    // For Google Cloud Postgresql
    // private final String host = "35.44.16.169";

    // For Local PostgreSQL
    private final String host = "127.0.0.1";

    private final String database = "madlane";
    //    private final String database = "madhosp";
    private final int port = 5432;
    private final String user = "postgres";
    private final String pass = "";
    private String url = "jdbc:postgresql://%s:%d/%s";
    private boolean status;

    private float[] coordinates = new float[2]; // nearest road point coordinates
    private float road_angle = 0; //angle of the road starting from north direction, clockwise positive
    private String road_name;
    private float lr_ref_angle = 0; //same as road_angle reference, if approaching, ped approach this angle
    private int road_lanes = -1;
    private int left_or_right = -1; // -1 for left, 1 for right

    public boolean query_status = false;
    private float st_d = 0;


    public jdbcDatabase()
    {
        this.url = String.format(this.url, this.host, this.port, this.database);
        connect();
        //this.disconnect();
        System.out.println("connection status:" + status);
    }

    public boolean checkConnection(){
        connect();
        System.out.println("connection status:" + status);
        return status;
    }

    private void connect()
    {
        Thread thread = new Thread(() -> {
            try
            {
                Class.forName("org.postgresql.Driver");
                connection = DriverManager.getConnection(url, user, pass);
                status = true;
                System.out.println("connected:" + status);
            }
            catch (Exception e)
            {
                status = false;
                System.out.print(e.getMessage());
                e.printStackTrace();
            }
        });
        thread.start();
        try
        {
            thread.join();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.status = false;
        }
    }

    public void getExtraConnection(String strLongi, String strLati)
    {
        Thread thread = new Thread(() -> {
            try
            {
                // Tested: one query takes around 60ms,
//                    System.out.println(System.currentTimeMillis());
                String SQL_SELECT = String.format("SELECT osm_id, lanes, name," +
                        "ST_X(ST_ClosestPoint(ST_Transform(r.way, 4326), point.geom))," +
                        "ST_Y(ST_ClosestPoint(ST_Transform(r.way, 4326), point.geom))," +
                        "ST_DistanceSphere(ST_ClosestPoint(ST_Transform(r.way, 4326), point.geom), point.geom)" +
                        " FROM planet_osm_line r," +
                        " (Select ST_SetSRID(ST_MakePoint(%s,%s), 4326) as geom) point " +
                        "WHERE osm_id > 0 ORDER BY 6 ASC LIMIT 10", strLongi, strLati);
                Class.forName("org.postgresql.Driver");
                if (connection == null) {
                    connection = DriverManager.getConnection(url, user, pass);
                }
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_SELECT);
                ResultSet resultSet = preparedStatement.executeQuery();
                float st_x = 0;
                float st_y = 0;
                String road_names = "";
                String lanes = "";
                // read first valid statement, has road names,
                while ((road_names.equals("") || road_names.contains("Railroad") ) && resultSet.next()) {
                    Long osm_id = resultSet.getLong("osm_id");
                    // road names is from resultSet if not null, otherwise it's ""
                    road_names = resultSet.getString("name") == null ? "" : resultSet.getString("name");
                    st_x = resultSet.getFloat("st_x");
                    st_y = resultSet.getFloat("st_y");
                    st_d = resultSet.getFloat("st_distancesphere");
                    lanes = resultSet.getString("lanes");
                    System.out.println(road_names);
                    System.out.println(String.valueOf(osm_id));
                    System.out.println(lanes);
                    System.out.println("NEW D Validation:"+String.valueOf(st_x) + " " + String.valueOf(st_y) + " " + String.valueOf(st_d));
                }
//                    System.out.println(System.currentTimeMillis());
                // if st_x or st_y is 0, then it's invalid statement, jump to exception
                if (st_x == 0 || st_y == 0) {
                    throw new Exception("Invalid statement");
                }
                // request nearest road angle
                String road_angles_sql = String.format("SELECT degrees(ST_Azimuth( ST_Point(%s,%s), ST_Point(%s,%s))) AS degA_B",
                        strLongi, strLati, st_x, st_y);
                preparedStatement = connection.prepareStatement(road_angles_sql);
                resultSet = preparedStatement.executeQuery();
                float degA_B = 0;
                while (resultSet.next()) {
                    degA_B = resultSet.getFloat("degA_B");
                    System.out.println(String.valueOf(degA_B));
                }

                if (connection != null) {
                    System.out.println("Connected to the database!");
                } else {
                    System.out.println("Failed to make connection!");
                }
                this.coordinates[0] = st_x;
                this.coordinates[1] = st_y;
                this.road_name = road_names;
                if (lanes != null) {
                    this.road_lanes = Integer.parseInt(lanes);
                }
                else {
                    this.road_lanes = -1;
                }
                //determin if N or S , E or W, st_x is longi, st_y is lati
                boolean isPedonRoadsNorth = true;
                boolean isPedonRoadEast = true;
                // if pedon is on the west of road point, its strLongi should be smaller than st_x
                if (Float.parseFloat(strLongi) < st_x) {
                    isPedonRoadEast = false;//West
                }
                // if pedon is on the south of road point, its strLati should be smaller than st_y
                if (Float.parseFloat(strLati) < st_y) {
                    isPedonRoadsNorth = false; //South
                }

                // get left_right reference and angles
                //road_angle is from east direction, we need to shift to north direction
                degA_B = (degA_B + 90 + 360) % 360;
                degA_B = degA_B % 180;
                int left_or_right = 0; // right 1, left -1
                if (degA_B >= 0 && degA_B < 45){
                    left_or_right = isPedonRoadEast ? 1 : -1;
                }
                else if (degA_B >= 45 && degA_B < 135){
                    left_or_right = isPedonRoadsNorth ? -1 : 1;
                }
                else if (degA_B >= 135 && degA_B < 180){
                    left_or_right = isPedonRoadEast ? -1 : 1;
                }
                System.out.println("left_or_right: " + String.valueOf(left_or_right));
                System.out.println("road_angle: " + String.valueOf(degA_B));
                //print north or south
                System.out.println("isPedonRoadsNorth: " + String.valueOf(isPedonRoadsNorth));
                System.out.println("isPedonRoadEast: " + String.valueOf(isPedonRoadEast));

                this.road_angle = degA_B;
                this.lr_ref_angle = (degA_B - 90 * left_or_right + 360) % 360;
                this.left_or_right = left_or_right;
//                sleep(1000);
                this.query_status = true;
            }
            catch (Exception e)
            {
                System.out.print(e.getMessage());
                e.printStackTrace();
                this.query_status = false;
            }
        });
        thread.start();
        try
        {
            thread.join();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.status = false;
            this.query_status = false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException ex) {
            System.out.println(ex.getMessage());
        }
    }

    public float[] getCoordinates() {
        System.out.println("get coordinates");
        System.out.println(String.valueOf(this.coordinates[0]) + " " + String.valueOf(this.coordinates[1]));
        // print all class values
        System.out.println(String.valueOf(this.road_angle));
        System.out.println(String.valueOf(this.lr_ref_angle));
        System.out.println(String.valueOf(this.road_lanes));
        System.out.println(this.road_name);
        return this.coordinates;
    }
    public float getRoadAngles() {
        return this.road_angle;
    }
    public String getRoadName() {
        return this.road_name;
    }
    public float get_lr_ref_angle() {
        return this.lr_ref_angle;
    }
    public int get_lr_ref(){
        return this.left_or_right;
    }

    // return distance to the nearest road
    public float getDistance(){
        return this.st_d;
    }
}
