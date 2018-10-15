package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for case study 1 - BIKE_USEAGE PREDICTION
 * @author Harshit Gupta / zhouzhou514
 *
 */
public class UBikeFog {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    static boolean CLOUD = true;

    static int numOfDepts = 4;
    static int numOfSitesPerDept = 10;
    static int numOfWorkersPerDept = 1;
    static double UBIKE_TRANSMISSION_TIME = 300;
    //static double UBIKE_TRANSMISSION_TIME = 10;

    public static void main(String[] args) {

        Log.printLine("Starting UBikeFog...");

        try {
            Log.disable();
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "ubikefog"; // identifier of the application

            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping

            if(CLOUD){
                // if the mode of deployment is cloud-based
				/*moduleMapping.addModuleToDevice("worker_reminder", "cloud", numOfDepts*numOfSitesPerDept); // fixing all instances of the Connector module to the Cloud
				moduleMapping.addModuleToDevice("bikedata_collector", "cloud", numOfDepts*numOfSitesPerDept); // fixing all instances of the Concentration Calculator module to the Cloud
*/				moduleMapping.addModuleToDevice("cloud_scheduler", "cloud"); // fixing all instances of the cloud prediction module to the Cloud
                for(FogDevice device : fogDevices){
                    if(device.getName().startsWith("d")){
                        //moduleMapping.addModuleToDevice("fog_predictor, device.getName(), 1);  // fixing all instances of the Client module to the Smartphones
                        moduleMapping.addModuleToDevice("fog_predictor", device.getName());  // fixing all instances of the Client module to the Smartphones
                    }
                    else if(device.getName().startsWith("ms")){
                        moduleMapping.addModuleToDevice("bikedata_collector",device.getName());
                    }
                    else if(device.getName().startsWith("mw")){
                        moduleMapping.addModuleToDevice("worker_reminder",device.getName());
                    }
                }
            }else{
                // if the mode of deployment is cloud-based
                //moduleMapping.addModuleToDevice("worker_reminder", "cloud", numOfDepts*numOfSitesPerDept); // fixing all instances of the Connector module to the Cloud
                moduleMapping.addModuleToDevice("worker_reminder", "cloud"); // fixing all instances of the Connector module to the Cloud
                // rest of the modules will be placed by the Edge-ward placement policy
            }


            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            controller.submitApplication(application, 0,
                    (CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            :(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("UBikeFog finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates the fog devices in the physical topology of the simulation.
     * @param userId
     * @param appId
     */
    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 40000, 40000, 100, 10000, 0, 0.01, 500, 300); // creates the fog device Cloud at the apex of the hierarchy with level=0
        cloud.setParentId(-1);
        //FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
        //proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
        //proxy.setUplinkLatency(100);
        fogDevices.add(cloud);
        //fogDevices.add(proxy);

        for(int i=0;i<numOfDepts;i++){
            addGw(i+"", userId, appId, cloud.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
        }

    }

    private static FogDevice addGw(String id, int userId, String appId, int parentId){	//Gate way
        FogDevice dept = createFogDevice("d-"+id, 10000, 10000, 1000, 2000, 1, 0.0, 300, 200);
        fogDevices.add(dept);
        dept.setParentId(parentId);
        dept.setUplinkLatency(500); // latency of connection between gateways and cloud server is 500 ms
        for(int i=0;i<numOfSitesPerDept;i++){
            String mobileId = id+"-"+i;
            FogDevice mobile = addSite(mobileId, userId, appId, dept.getId()); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
            mobile.setUplinkLatency(100); // latency of connection between the FogServer and Site is 100 ms
            fogDevices.add(mobile);
        }
        for(int i=0;i<numOfWorkersPerDept;i++){
            String workerId = id+"-"+i;
            FogDevice worker = addWorker(workerId, userId, appId, dept.getId()); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
            worker.setUplinkLatency(100); // latency of connection between the FogServer and Site is 100 ms
            fogDevices.add(worker);
        }
        return dept;
    }

    private static FogDevice addSite(String id, int userId, String appId, int parentId){
        FogDevice mobile = createFogDevice("ms-"+id, 1000, 1000, 100, 1000, 2, 0, 100, 70);
        mobile.setParentId(parentId);
        Sensor ubikeSensor = new Sensor("s-"+id, "BIKE_USEAGE", userId, appId, new DeterministicDistribution(UBIKE_TRANSMISSION_TIME)); // inter-transmission time of BIKE_USEAGE sensor follows a deterministic distribution
        sensors.add(ubikeSensor);
        ubikeSensor.setGatewayDeviceId(mobile.getId());
        ubikeSensor.setLatency(10.0);  // latency of connection between BIKE_USEAGE sensors and the parent Site is 10 ms
        return mobile;
    }
    private static FogDevice addWorker(String id, int userId, String appId, int parentId){
        FogDevice mobile = createFogDevice("mw-"+id, 1000, 1000, 100, 1000, 2, 0, 100, 70);
        mobile.setParentId(parentId);
        Actuator reminder = new Actuator("a-"+id, userId, appId, "REMINDER_MSG");
        actuators.add(reminder);
        reminder.setGatewayDeviceId(mobile.getId());
        reminder.setLatency(10);  // latency of connection between reminder actuator and the Smartphone is 10 ms
        return mobile;
    }
    /**
     * Creates a vanilla fog device
     * @param nodeName name of the device to be used in simulatio
     * @param mips MIPS
     * @param ram RAM
     * @param upBw uplink bandwidth
     * @param downBw downlink bandwidth
     * @param level hierarchy level of the device
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 100000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the BIKE_USEAGE predictor application in the DDF model.
     * @param appId unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial" })
    private static Application createApplication(String appId, int userId){

        Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)

        /*
         * Adding modules (vertices) to the application model (directed graph)
         */
        application.addAppModule("cloud_scheduler", 10);
        application.addAppModule("fog_predictor", 10); // adding module Client to the application model
        application.addAppModule("bikedata_collector", 10); // adding module Concentration Calculator to the application model
        application.addAppModule("worker_reminder", 10); // adding module Connector to the application model
        /*
         * Connecting the application modules (vertices) in the application model (directed graph) with edges
         */
        if(UBIKE_TRANSMISSION_TIME==300)
            application.addAppEdge("BIKE_USEAGE", "bikedata_collector", 2000, 500, "BIKE_USEAGE", Tuple.UP, AppEdge.SENSOR); // adding edge from BIKE_USEAGE (sensor) to Client module carrying tuples of type BIKE_USEAGE
        else
            application.addAppEdge("BIKE_USEAGE", "bikedata_collector", 3000, 500, "BIKE_USEAGE", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("bikedata_collector", "fog_predictor", 14, 500, "SITE_STATE", Tuple.UP, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
        application.addAppEdge("fog_predictor", "worker_reminder", 100, 28, 1000, "SCHEDULE_COMMAND", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
        application.addAppEdge("worker_reminder", "REMINDER_MSG", 1000, 500, "REMINDER_MSG", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
        application.addAppEdge("fog_predictor", "cloud_scheduler", 1000, 500, "BLOCK_STATE", Tuple.UP, AppEdge.MODULE);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
        application.addAppEdge("cloud_scheduler", "worker_reminder", 1000, 500, "CROSS_BLOCK_SCHEDULE", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE


        /*
         * Defining the input-output relationships (represented by selectivity) of the application modules.
         */
        application.addTupleMapping("fog_predictor", "SITE_STATE", "SCHEDULE_COMMAND", new FractionalSelectivity(0.05)); // 0.9 tuples of type   are emitted by Client module per incoming tuple of type BIKE_USEAGE
        application.addTupleMapping("fog_predictor", "SITE_STATE", "BLOCK_STATE", new FractionalSelectivity(0.01)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION
        application.addTupleMapping("bikedata_collector", "BIKE_USEAGE", "SITE_STATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type
        application.addTupleMapping("worker_reminder", "SCHEDULE_COMMAND", "REMINDER_MSG", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE
        application.addTupleMapping("worker_reminder", "CROSS_BLOCK_SCHEDULE", "REMINDER_MSG", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE
        application.addTupleMapping("cloud_scheduler", "BLOCK_STATE", "CROSS_BLOCK_SCHEDULE", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE

        /*
         * Defining application loops to monitor the latency of.
         * Here, we add only one loop for monitoring : BIKE_USEAGE(sensor) -> Client -> Concentration Calculator -> Client -> REMINDER_MSG (actuator)
         */
        final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("BIKE_USEAGE");add("bikedata_collector");add("fog_predictor");add("worker_reminder");add("REMINDER_MSG");}});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("BIKE_USEAGE");add("bikedata_collector");add("fog_predictor");add("cloud_scheduler");add("worker_reminder");add("REMINDER_MSG");}});
        List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};
        application.setLoops(loops);

        return application;
    }
}