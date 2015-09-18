package com.hazelcast.simulator.provisioner;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.Listener;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;
import com.hazelcast.simulator.common.AgentsFile;
import com.hazelcast.simulator.common.SimulatorProperties;
import com.hazelcast.simulator.protocol.registry.AgentData;
import com.hazelcast.simulator.protocol.registry.ComponentRegistry;
import com.hazelcast.simulator.utils.CommandLineExitException;
import org.apache.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import static com.hazelcast.simulator.utils.CommonUtils.exitWithError;
import static com.hazelcast.simulator.utils.CommonUtils.sleepSeconds;
import static com.hazelcast.simulator.utils.FileUtils.appendText;
import static com.hazelcast.simulator.utils.SimulatorUtils.loadComponentRegister;

/**
 * An AWS specific provisioning class which is using the AWS SDK to create AWS instances and AWS elastic load balancer.
 */
@SuppressWarnings("checkstyle:classdataabstractioncoupling")
public class AwsProvisioner {

    // the file which will holds the public domain name of the created load balancer
    private static final String AWS_ELB_FILE_NAME = "aws-elb.txt";

    // AWS specific magic strings
    private static final String AWS_RUNNING_STATE = "running";
    private static final String AWS_SYSTEM_STATUS_OK = "ok";
    private static final String AWS_PUBLIC_IP_FILTER = "ip-address";
    private static final String AWS_KET_NAME_FILTER = "key-name";

    private static final int SLEEPING_MS = 1000 * 30;
    private static final int MAX_SLEEPING_ITERATIONS = 12;
    private static final Logger LOGGER = Logger.getLogger(Provisioner.class);

    private final File agentsFile = new File(AgentsFile.NAME);
    private final File elbFile = new File(AWS_ELB_FILE_NAME);

    private final ComponentRegistry componentRegistry;

    private AmazonEC2 ec2;
    private AmazonElasticLoadBalancingClient elb;

    private String securityGroup;
    private String awsKeyName;
    private String awsAmi;
    private String awsBoxId;
    private String subNetId;

    private String elbProtocol;
    private int elbPortIn;
    private int elbPortOut;
    private String elbAvailabilityZones;

    AwsProvisioner() {
        componentRegistry = loadComponentRegister(agentsFile, false);
        setProperties(null);
    }

    void setProperties(File file) {
        SimulatorProperties simulatorProperties = new SimulatorProperties();
        simulatorProperties.init(file);

        String awsCredentialsPath = simulatorProperties.get("AWS_CREDENTIALS", "awscredentials.properties");
        File credentialsFile = new File(awsCredentialsPath);

        elbProtocol = simulatorProperties.get("ELB_PROTOCOL");

        elbPortIn = Integer.parseInt(simulatorProperties.get("ELB_PORT_IN", "0"));
        elbPortOut = Integer.parseInt(simulatorProperties.get("ELB_PORT_OUT", "0"));
        elbAvailabilityZones = simulatorProperties.get("ELB_ZONES");

        awsKeyName = simulatorProperties.get("AWS_KEY_NAME");
        awsAmi = simulatorProperties.get("AWS_AMI");
        awsBoxId = simulatorProperties.get("AWS_BOXID");
        securityGroup = simulatorProperties.get("SECURITY_GROUP");
        subNetId = simulatorProperties.get("SUBNET_ID", "");

        try {
            AWSCredentials credentials = new PropertiesCredentials(credentialsFile);

            ec2 = new AmazonEC2Client(credentials);
            elb = new AmazonElasticLoadBalancingClient(credentials);
        } catch (Exception e) {
            throw new CommandLineExitException("Credentials file could not be loaded", e);
        }
    }

    void shutdown() {
        if (ec2 != null) {
            ec2.shutdown();
        }
        if (elb != null) {
            elb.shutdown();
        }
    }

    String createLoadBalancer(String name) {
        CreateLoadBalancerRequest request = new CreateLoadBalancerRequest();
        request.setLoadBalancerName(name);
        request.withAvailabilityZones(elbAvailabilityZones.split(","));

        List<Listener> listeners = new ArrayList<Listener>();
        listeners.add(new Listener(elbProtocol, elbPortIn, elbPortOut));
        request.setListeners(listeners);

        CreateLoadBalancerResult lbResult = elb.createLoadBalancer(request);
        appendText(lbResult.getDNSName() + "\n", elbFile);

        return request.getLoadBalancerName();
    }

    void addAgentsToLoadBalancer(String elbName) {
        if (!isBalancerAlive(elbName)) {
            createLoadBalancer(elbName);
        }

        List<Instance> instances = getInstancesByPublicIp(componentRegistry.getAgents());
        addInstancesToElb(elbName, instances);
    }

    void scaleInstanceCountTo(int totalInstancesWanted) {
        int agentsSize = componentRegistry.agentCount();
        if (totalInstancesWanted > agentsSize) {
            createInstances(totalInstancesWanted - agentsSize);
        } else {
            terminateInstances(agentsSize - totalInstancesWanted);
        }
    }

    private List<Instance> createInstances(int instanceCount) {
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(awsAmi)
                .withInstanceType(awsBoxId)
                .withMinCount(instanceCount)
                .withMaxCount(instanceCount)
                .withKeyName(awsKeyName);

        if (subNetId.isEmpty()) {
            runInstancesRequest.withSecurityGroups(securityGroup);
        } else {
            runInstancesRequest.withSubnetId(subNetId);
        }

        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);

        List<Instance> checkedInstances = new ArrayList<Instance>();
        List<Instance> instances = runInstancesResult.getReservation().getInstances();
        for (Instance instance : instances) {
            if (waitForInstanceStatusRunning(instance)) {
                addInstanceToAgentsFile(instance);
                checkedInstances.add(instance);
            } else {
                LOGGER.warn("Timeout waiting for running status id=" + instance.getInstanceId());
            }
        }
        return checkedInstances;
    }

    private void terminateInstances(int count) {
        List<AgentData> deadList = componentRegistry.getAgents(count);
        List<Instance> deadInstances = getInstancesByPublicIp(deadList);

        LOGGER.info("Updating " + agentsFile.getAbsolutePath());
        AgentsFile.save(agentsFile, componentRegistry);

        terminateInstances(deadInstances);
    }

    private void terminateInstances(List<Instance> instances) {
        List<String> ids = new ArrayList<String>();
        for (Instance instance : instances) {
            ids.add(instance.getInstanceId());
        }

        TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();
        terminateInstancesRequest.withInstanceIds(ids);
        ec2.terminateInstances(terminateInstancesRequest);
    }

    private List<Instance> getInstancesByPublicIp(List<AgentData> agentDataList) {
        List<String> ips = new ArrayList<String>();
        for (AgentData agentData : agentDataList) {
            componentRegistry.removeAgent(agentData);
            ips.add(agentData.getPublicAddress());
        }

        DescribeInstancesRequest request = new DescribeInstancesRequest();
        Filter filter = new Filter(AWS_PUBLIC_IP_FILTER, ips);

        DescribeInstancesResult result = ec2.describeInstances(request.withFilters(filter));
        List<Reservation> reservations = result.getReservations();
        List<Instance> foundInstances = new ArrayList<Instance>();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            foundInstances.addAll(instances);
        }
        return foundInstances;
    }

    private boolean waitForInstanceStatusRunning(Instance instance) {
        String instanceId = instance.getInstanceId();
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
        int counter = 0;
        while (counter++ < MAX_SLEEPING_ITERATIONS) {
            sleepSeconds(SLEEPING_MS);

            DescribeInstancesResult result = ec2.describeInstances(describeInstancesRequest);
            for (Reservation reservation : result.getReservations()) {
                for (Instance reserved : reservation.getInstances()) {
                    if (reserved.getPublicIpAddress() != null && AWS_RUNNING_STATE.equals(reserved.getState().getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void addInstanceToAgentsFile(Instance instance) {
        String instanceId = instance.getInstanceId();
        DescribeInstancesRequest describeInstancesRequest = new DescribeInstancesRequest().withInstanceIds(instanceId);
        DescribeInstancesResult result = ec2.describeInstances(describeInstancesRequest);

        for (Reservation reservation : result.getReservations()) {
            for (Instance reserved : reservation.getInstances()) {
                appendText(reserved.getPublicIpAddress() + "," + reserved.getPrivateIpAddress() + "\n", agentsFile);
            }
        }
    }

    private boolean isBalancerAlive(String name) {
        Collection<String> names = new HashSet<String>();
        names.add(name);

        DescribeLoadBalancersRequest describe = new DescribeLoadBalancersRequest();
        describe.setLoadBalancerNames(names);

        try {
            DescribeLoadBalancersResult result = elb.describeLoadBalancers(describe);
            List<LoadBalancerDescription> description = result.getLoadBalancerDescriptions();

            if (description.isEmpty()) {
                return false;
            }
            return true;

        } catch (AmazonServiceException e) {
            LOGGER.fatal("Exception in isBalancerAlive(" + name + ")", e);
        }
        return false;
    }

    private void addInstancesToElb(String name, List<Instance> instances) {
        if (instances.isEmpty()) {
            LOGGER.info("No instances to add to load balance " + name);
            return;
        }

        // get instance ids
        Collection<com.amazonaws.services.elasticloadbalancing.model.Instance> ids
                = new ArrayList<com.amazonaws.services.elasticloadbalancing.model.Instance>();
        for (Instance instance : instances) {
            ids.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(instance.getInstanceId()));
        }

        // register the instances to the balancer
        RegisterInstancesWithLoadBalancerRequest register = new RegisterInstancesWithLoadBalancerRequest();
        register.setLoadBalancerName(name);
        register.setInstances(ids);
        elb.registerInstancesWithLoadBalancer(register);
    }

    public static void main(String[] args) {
        try {
            LOGGER.info("AWS specific provisioner");

            AwsProvisioner provisioner = new AwsProvisioner();
            AwsProvisionerCli cli = new AwsProvisionerCli(provisioner, args);

            cli.run();
        } catch (Exception e) {
            exitWithError(LOGGER, "Could not provision machines", e);
        }
    }
}
