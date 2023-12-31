package com.cytek2.cytek.audit.listeners;

import com.cytek2.cytek.audit.model.EnergyData;
import com.cytek2.cytek.audit.model.Meter;
import com.cytek2.cytek.audit.model.User;
import com.cytek2.cytek.audit.repository.EnergyDataRepository;
import com.cytek2.cytek.audit.repository.MeterRepository;
import com.cytek2.cytek.audit.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Component
public class EnergyMeterListener {

    private final EnergyDataRepository energyDataRepository;
    private final MeterRepository meterRepository;
    private final UserRepository userRepository;
    private  MqttClient mqttClient;
    private final ExecutorService executorService;

    public EnergyMeterListener( EnergyDataRepository energyDataRepository, MeterRepository meterRepository, UserRepository userRepository) {
        this.energyDataRepository = energyDataRepository;
        this.meterRepository = meterRepository;
        this.userRepository = userRepository;

        this.executorService = Executors.newCachedThreadPool();

        try {
            this.mqttClient = new MqttClient("tcp://mqtt.iammeter.com:1883", "EnergyMeterListener");
            // Connect to MQTT broker
            connectToMqttBroker();

            // Subscribe to meter topics
            subscribeToMeterTopics();
        } catch (MqttException e) {
            e.printStackTrace();
            // Handle the exception as needed, e.g., log the error or terminate the application
        }
    }




    private void connectToMqttBroker() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setUserName("meter_002");
        connectOptions.setPassword("20232023".toCharArray());

        connectOptions.setCleanSession(true);

        try {
            mqttClient.connect(connectOptions);
            System.out.println("Connected to MQTT broker");
        } catch (MqttException e) {
            e.printStackTrace();
            System.err.println("Failed to connect to MQTT broker: " + e.getMessage());
            // Handle the exception as needed
        }
    }


    private void subscribeToMeterTopics() {
        // Fetch all serial numbers from the database
        List<String> serialNumbers = meterRepository.findSerialNumbers();


        for (String serialNumber : serialNumbers) {
            executorService.submit(() -> {
                int maxAttempts = 1;
                int attempt = 0;
                boolean subscribed = false;

                // Inside the subscribeToMeterTopics() method
                while (!subscribed && attempt < maxAttempts) {
                    try {
                        String topic = "device/" + serialNumber + "/realtime";
                        mqttClient.subscribe(topic, new IMqttMessageListener() {
                            @Override
                            public void messageArrived(String topic, MqttMessage message) throws Exception {
                                // This callback is invoked when a message is received on the subscribed topic
                                handleMessage(topic, message); // Call handleMessage with the received message
                            }
                        });
                        System.out.println("Subscribed to topic: " + topic);
                        subscribed = true; // Successfully subscribed
                    } catch (MqttException e) {
                        e.printStackTrace();
                        System.err.println("Failed to subscribe to topic: " + serialNumber);

                        // Implement a backoff delay before retrying
                        try {
                            Thread.sleep(2000); // Sleep for 2 seconds before retrying
                        } catch (InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                        attempt++; // Increment the retry attempt
                    }
                }


                if (!subscribed) {
                    // If not subscribed after maxAttempts, continue with other meters
                    System.err.println("Trying the next smart meter with serial number: " + serialNumber);
                }
            });
        }
    }




    public void handleMessage(String topic, MqttMessage message) throws Exception {
        String data = new String(message.getPayload(), StandardCharsets.UTF_8);
        System.out.println("Received message on topic '" + topic + "': " + data);

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(data);
            String serialNumber = jsonNode.path("SN").asText();

            System.out.println("Received Serial Number: " + serialNumber);

            System.out.println("Started looking for data of all meters");
            List<Object[]> meterDetailsList = meterRepository.findMeterDetails();
            System.out.println("data=");

            for (Object[] meterDetails : meterDetailsList) {
                Long meterId = (Long) meterDetails[0];
                String serialNumberFromDetails = (String) meterDetails[1];
                Integer userId = (Integer) meterDetails[2];

                System.out.println("Meter ID: " + meterId);
                System.out.println("Serial Number: " + serialNumberFromDetails);
                System.out.println("User ID: " + userId);




                System.out.println("check if" +serialNumber+"="+serialNumberFromDetails);
                if (serialNumber.equals(serialNumberFromDetails)) {
                    System.out.println("they are equal");
                    if(userId !=null){
                            System.out.println("ready to save data now after finding user and meter");
                            double redVoltage = jsonNode.get("Datas").get(0).get(0).asDouble();
                            double yellowVoltage = jsonNode.get("Datas").get(1).get(0).asDouble();
                            double blueVoltage = jsonNode.get("Datas").get(2).get(0).asDouble();

                            double redCurrent = jsonNode.get("Datas").get(0).get(1).asDouble();
                            double yellowCurrent = jsonNode.get("Datas").get(1).get(1).asDouble();
                            double blueCurrent = jsonNode.get("Datas").get(2).get(1).asDouble();

                            double redPower = jsonNode.get("Datas").get(0).get(2).asDouble();
                            double yellowPower = jsonNode.get("Datas").get(1).get(2).asDouble();
                            double bluePower = jsonNode.get("Datas").get(2).get(2).asDouble();

                            double redPowerConsumption = jsonNode.get("Datas").get(0).get(3).asDouble();
                            double yellowPowerConsumption = jsonNode.get("Datas").get(1).get(3).asDouble();
                            double bluePowerConsumption = jsonNode.get("Datas").get(2).get(3).asDouble();

                            double redExportedEnergy = jsonNode.get("Datas").get(0).get(4).asDouble();
                            double yellowExportedEnergy = jsonNode.get("Datas").get(1).get(4).asDouble();
                            double blueExportedEnergy = jsonNode.get("Datas").get(2).get(4).asDouble();

                            double redFrequency = jsonNode.get("Datas").get(0).get(5).asDouble();
                            double yellowFrequency = jsonNode.get("Datas").get(1).get(5).asDouble();
                            double blueFrequency = jsonNode.get("Datas").get(2).get(5).asDouble();

                            double redFactor = jsonNode.get("Datas").get(0).get(6).asDouble();
                            double yellowFactor = jsonNode.get("Datas").get(1).get(6).asDouble();
                            double blueFactor = jsonNode.get("Datas").get(2).get(6).asDouble();


                            System.out.println("extracting data after finding user and meter....");

                            // Create an EnergyData entity and set the extracted values
                            EnergyData energyData = new EnergyData();
                            energyData.setRedVoltage(redVoltage);
                            energyData.setYellowVoltage(yellowVoltage);
                            energyData.setBlueVoltage(blueVoltage);
                            energyData.setRedCurrent(redCurrent);
                            energyData.setYellowCurrent(yellowCurrent);
                            energyData.setBlueCurrent(blueCurrent);
                            energyData.setRedPower(redPower);
                            energyData.setYellowPower(yellowPower);
                            energyData.setBluePower(bluePower);
                            energyData.setRedPowerConsumption(redPowerConsumption);
                            energyData.setYellowPowerConsumption(yellowPowerConsumption);
                            energyData.setBluePowerConsumption(bluePowerConsumption);
                            energyData.setRedExportedEnergy(redExportedEnergy);
                            energyData.setYellowExportedEnergy(yellowExportedEnergy);
                            energyData.setBlueExportedEnergy(blueExportedEnergy);
                            energyData.setRedFrequency(redFrequency);
                            energyData.setYellowFrequency(yellowFrequency);
                            energyData.setBlueFrequency(blueFrequency);
                            energyData.setRedPowerFactor(redFactor);
                            energyData.setYellowPowerFactor(yellowFactor);
                            energyData.setBluePowerFactor(blueFactor);


                            // Set the date to the current date
                            LocalDate currentDate = LocalDate.now();
                            energyData.setDate(Date.valueOf(currentDate).toLocalDate());

                            // Set the current day (day of the week)
                            String currentDay = currentDate.getDayOfWeek().name();
                            energyData.setDay(currentDay);


                            // Set the time to the current time
                            LocalTime currentTime = LocalTime.now();
                            energyData.setTime(Time.valueOf(currentTime));

                        energyData.setUserId(userId);
                        energyData.setMeterId(meterId);

                            System.out.println("Data to be saved: " + energyData);
                            // Save the entity to the database
                            energyDataRepository.save(energyData);
                            System.out.println("Data saved: " + energyData);

                    }
                    else{


                            System.out.println("ready to save data now after finding meter only");

                            double redVoltage = jsonNode.get("Datas").get(0).get(0).asDouble();
                            double yellowVoltage = jsonNode.get("Datas").get(1).get(0).asDouble();
                            double blueVoltage = jsonNode.get("Datas").get(2).get(0).asDouble();

                            double redCurrent = jsonNode.get("Datas").get(0).get(1).asDouble();
                            double yellowCurrent = jsonNode.get("Datas").get(1).get(1).asDouble();
                            double blueCurrent = jsonNode.get("Datas").get(2).get(1).asDouble();

                            double redPower = jsonNode.get("Datas").get(0).get(2).asDouble();
                            double yellowPower = jsonNode.get("Datas").get(1).get(2).asDouble();
                            double bluePower = jsonNode.get("Datas").get(2).get(2).asDouble();

                            double redPowerConsumption = jsonNode.get("Datas").get(0).get(3).asDouble();
                            double yellowPowerConsumption = jsonNode.get("Datas").get(1).get(3).asDouble();
                            double bluePowerConsumption = jsonNode.get("Datas").get(2).get(3).asDouble();

                            double redExportedEnergy = jsonNode.get("Datas").get(0).get(4).asDouble();
                            double yellowExportedEnergy = jsonNode.get("Datas").get(1).get(4).asDouble();
                            double blueExportedEnergy = jsonNode.get("Datas").get(2).get(4).asDouble();

                            double redFrequency = jsonNode.get("Datas").get(0).get(5).asDouble();
                            double yellowFrequency = jsonNode.get("Datas").get(1).get(5).asDouble();
                            double blueFrequency = jsonNode.get("Datas").get(2).get(5).asDouble();

                            double redFactor = jsonNode.get("Datas").get(0).get(6).asDouble();
                            double yellowFactor = jsonNode.get("Datas").get(1).get(6).asDouble();
                            double blueFactor = jsonNode.get("Datas").get(2).get(6).asDouble();


                            System.out.println("extracting data after findig meter....");

                            // Create an EnergyData entity and set the extracted values
                            EnergyData energyData = new EnergyData();
                            energyData.setRedVoltage(redVoltage);
                            energyData.setYellowVoltage(yellowVoltage);
                            energyData.setBlueVoltage(blueVoltage);
                            energyData.setRedCurrent(redCurrent);
                            energyData.setYellowCurrent(yellowCurrent);
                            energyData.setBlueCurrent(blueCurrent);
                            energyData.setRedPower(redPower);
                            energyData.setYellowPower(yellowPower);
                            energyData.setBluePower(bluePower);
                            energyData.setRedPowerConsumption(redPowerConsumption);
                            energyData.setYellowPowerConsumption(yellowPowerConsumption);
                            energyData.setBluePowerConsumption(bluePowerConsumption);
                            energyData.setRedExportedEnergy(redExportedEnergy);
                            energyData.setYellowExportedEnergy(yellowExportedEnergy);
                            energyData.setBlueExportedEnergy(blueExportedEnergy);
                            energyData.setRedFrequency(redFrequency);
                            energyData.setYellowFrequency(yellowFrequency);
                            energyData.setBlueFrequency(blueFrequency);
                            energyData.setRedPowerFactor(redFactor);
                            energyData.setYellowPowerFactor(yellowFactor);
                            energyData.setBluePowerFactor(blueFactor);


                        // Set the date to the current date without the time
                        LocalDate currentDate = LocalDate.now();
                        String formattedDate = currentDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        energyData.setDate(Date.valueOf(formattedDate).toLocalDate());

                        // Set the current day (day of the week)
                            String currentDay = currentDate.getDayOfWeek().name();
                            energyData.setDay(currentDay);


                            // Set the time to the current time
                            LocalTime currentTime = LocalTime.now();
                            energyData.setTime(Time.valueOf(currentTime));

//                        System.out.println("setting user id to:" + user);

                            System.out.println("setting meter id to:" + meterId);

                            energyData.setMeterId(meterId);

                            System.out.println("Data to be saved: " + energyData);
                            // Save the entity to the database
                            energyDataRepository.save(energyData);
                            System.out.println("Data saved: " + energyData);


                    }

                }
            }





        } catch (IOException e) {
            e.printStackTrace();
            System.out.print("Error occured while saving data");
        }
    }



    public void connectionLost(Throwable cause) {
        // Handle connection loss
        System.out.println("Connection to the smart meter lost: " + cause.getMessage());
        // Implement reconnection logic here
        try {
            IMqttAsyncClient mqttClient = null;
            if (!mqttClient.isConnected()) {
                mqttClient.reconnect();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


    public void deliveryComplete(IMqttDeliveryToken token) {
        // Handle message delivery (QoS 1 and 2)
    }



    public void shutdown() throws MqttException {
        mqttClient.disconnect();
        mqttClient.close();
        executorService.shutdown();
    }
}
