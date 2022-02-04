// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.system.plant.DCMotor;
import edu.wpi.first.math.trajectory.TrapezoidProfile.Constraints;
import edu.wpi.first.math.util.Units;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Preferences;
import edu.wpi.first.wpilibj.RobotController;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.motorcontrol.PWMSparkMax;
import edu.wpi.first.wpilibj.simulation.BatterySim;
import edu.wpi.first.wpilibj.simulation.EncoderSim;
import edu.wpi.first.wpilibj.simulation.RoboRioSim;
import edu.wpi.first.wpilibj.simulation.SingleJointedArmSim;
import edu.wpi.first.wpilibj.smartdashboard.Mechanism2d;
import edu.wpi.first.wpilibj.smartdashboard.MechanismLigament2d;
import edu.wpi.first.wpilibj.smartdashboard.MechanismRoot2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.util.Color;
import edu.wpi.first.wpilibj.util.Color8Bit;
import edu.wpi.first.wpilibj2.command.ProfiledPIDCommand;

/** This is a sample program to demonstrate the use of arm simulation with existing code. */
public class Robot extends TimedRobot {
  private static final int kMotorPort = 0;
  private static final int kEncoderAChannel = 0;
  private static final int kEncoderBChannel = 1;
  private static final int kJoystickPort = 0;

  public static final String kArmPositionKey = "ArmPosition";
  public static final String kArmPKey = "ArmP";

  // The P gain for the PID controller that drives this arm.
  private static double kArmKp = 50.0;
  private static double armPositionDeg = 90.0;

  private double setpoint = armPositionDeg;

  // distance per pulse = (angle per revolution) / (pulses per revolution)
  //  = (2 * PI rads) / (4096 pulses)
  // TODO: change to through REV bore encoder 8192 pulses per revolution
  private static final double kArmEncoderDistPerPulse = 2.0 * Math.PI / 4096;

  // The arm gearbox represents a gearbox containing two NEO motors.
  private final DCMotor m_armGearbox = DCMotor.getNEO(2);

  // Standard classes for controlling our arm
  //private final PIDController m_controller = new PIDController(kArmKp, 0, 0);
  private final ProfiledPIDController m_controller = new ProfiledPIDController(kArmKp, 0, 0, new Constraints(Math.PI/2, Math.PI/2));
  private final Encoder m_encoder = new Encoder(kEncoderAChannel, kEncoderBChannel);
  private final PWMSparkMax m_motor = new PWMSparkMax(kMotorPort);
  private final Joystick m_joystick = new Joystick(kJoystickPort);

  // Simulation classes help us simulate what's going on, including gravity.
  private static final double m_armReduction = 70.31;
  private static final double m_armMass = Units.lbsToKilograms(145); // Kilograms
  private static final double m_armLength = Units.inchesToMeters(12);
  // This arm sim represents an arm that can travel from 45 degrees (up to the right)
  // to 100 degrees (up and to the left, almost straight up).
  private final SingleJointedArmSim m_armSim =
      new SingleJointedArmSim(
          m_armGearbox,
          m_armReduction,
          SingleJointedArmSim.estimateMOI(m_armLength, m_armMass),
          m_armLength,
          Units.degreesToRadians(30),
          Units.degreesToRadians(120),
          m_armMass,
          true,
          VecBuilder.fill(kArmEncoderDistPerPulse) // Add noise with a std-dev of 1 tick
          );
  private final EncoderSim m_encoderSim = new EncoderSim(m_encoder);

  // Create a Mechanism2d display of an Arm with a fixed ArmTower and moving Arm.
  private final Mechanism2d m_mech2d = new Mechanism2d(60, 60);
  private final MechanismRoot2d m_armPivot = m_mech2d.getRoot("ArmPivot", 30, 30);
  private final MechanismLigament2d m_armTower =
      m_armPivot.append(new MechanismLigament2d("ArmTower", 30, -90));
  private final MechanismLigament2d m_arm =
      m_armPivot.append(
          new MechanismLigament2d(
              "Arm",
              30,
              Units.radiansToDegrees(m_armSim.getAngleRads()),
              6,
              new Color8Bit(Color.kYellow)));

  @Override
  public void robotInit() {
    m_encoder.setDistancePerPulse(kArmEncoderDistPerPulse);

    // Put Mechanism 2d to SmartDashboard
    SmartDashboard.putData("Arm Sim", m_mech2d);
    m_armTower.setColor(new Color8Bit(Color.kBlue));

    // Set the Arm position setpoint and P constant to Preferences if the keys don't already exist
    if (!Preferences.containsKey(kArmPositionKey)) {
      Preferences.setDouble(kArmPositionKey, armPositionDeg);
    }
    if (!Preferences.containsKey(kArmPKey)) {
      Preferences.setDouble(kArmPKey, kArmKp);
    }
  }

  @Override
  public void simulationPeriodic() {
    // In this method, we update our simulation of what our arm is doing
    // First, we set our "inputs" (voltages)
    m_armSim.setInput(m_motor.get() * RobotController.getBatteryVoltage());

    // Next, we update it. The standard loop time is 20ms.
    m_armSim.update(0.020);

    // Finally, we set our simulated encoder's readings and simulated battery voltage
    m_encoderSim.setDistance(m_armSim.getAngleRads());
    // SimBattery estimates loaded battery voltages
    RoboRioSim.setVInVoltage(
        BatterySim.calculateDefaultBatteryLoadedVoltage(m_armSim.getCurrentDrawAmps()));

    // Update the Mechanism Arm angle based on the simulated arm angle
    m_arm.setAngle(Units.radiansToDegrees(m_armSim.getAngleRads()));
  }

  @Override
  public void teleopInit() {
    // Read Preferences for Arm setpoint and kP on entering Teleop
    armPositionDeg = Preferences.getDouble(kArmPositionKey, armPositionDeg);
    if (kArmKp != Preferences.getDouble(kArmPKey, kArmKp)) {
      kArmKp = Preferences.getDouble(kArmPKey, kArmKp);
      m_controller.setP(kArmKp);
    }
  }

  @Override
  public void teleopPeriodic() {
    if (m_joystick.getRawButton(1)) {
      setpoint = 110.0;
    } else  if (m_joystick.getRawButton(2)) {
      setpoint = 90.0;
    } else if (m_joystick.getRawButton(3)) {
      setpoint = 45.0;
    }

    var pidOutput =
      m_controller.calculate(m_encoder.getDistance(), Units.degreesToRadians(setpoint));
    m_motor.setVoltage(pidOutput);
  }

  @Override
  public void disabledInit() {
    // This just makes sure that our simulation code knows that the motor's off.
    m_motor.set(0.0);
  }
}
