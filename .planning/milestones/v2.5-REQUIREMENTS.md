# Requirements - Milestone v2.5: Hardened EKF Localization & Dynamic Sensor Fusion

Active requirements for hardening robot localization, EKF pose estimation, outlier filters, and dynamic covariance scaling under slippage, tilt, speed, and distance limits.

## Active Requirements

### Category: 3D Absolute Spatial Boundaries
- [ ] **HARDEN-01 (3D Bounds)**: Discard AprilTag visual estimates if target coordinates fall outside the physical field perimeter (X/Y limits) or vertical expectations (negative/underground Z or high floating Z values).

### Category: High-Speed Dynamics Vision Protection
- [ ] **HARDEN-02 (Motion Blur Lockout)**: Ignore visual measurements when the robot's instantaneous angular velocity (measured by Pigeon 2 / Pinpoint gyroscope) exceeds $2.0\text{ rad/s}$ ($115^{\circ}/\text{s}$) to avoid blurred frames.
- [ ] **HARDEN-03 (Collision Shock Guard)**: Reject camera measurements when linear acceleration exceeds a safe shock threshold ($2.5\text{ G}$), indicating a collision or vibration that degrades perspective transform math.

### Category: Odometry Tilt & Beaching Safety
- [ ] **HARDEN-04 (Tilt Protection)**: Monitor IMU pitch and roll angles. Dynamically scale up the wheel odometry state covariance $Q$ by $100\times$ when absolute tilt exceeds $8^{\circ}$ (slippage), and fully freeze encoder odometry inputs if tilt exceeds $15^{\circ}$ (beached wheels).

### Category: Elite Distance & Multi-Tag Scaling
- [ ] **HARDEN-05 (Distance Noise Scaling)**: Dynamically scale measurement noise standard deviation $R$ quadratically with distance $d$ from the camera to the tag to trust distant observations less.
- [ ] **HARDEN-06 (Multi-Tag Multiplier)**: Scale down standard deviations (increasing measurement trust weight) by a factor of $\frac{1}{\sqrt{\text{numTags}}}$ when multiple tags are visible in a single sensor frame.
