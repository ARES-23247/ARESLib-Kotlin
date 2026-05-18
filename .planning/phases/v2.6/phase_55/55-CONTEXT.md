# Phase 55: VFH+ Obstacle Avoidance Detours & Simulator Verification - Context

We are implementing a lightweight Vector Field Histogram (VFH+) steering algorithm inside the `HolonomicDriveController` and verifying it via closed-loop simulations with simulated obstacles.

## Mathematical Details

### 1. Vector Field Histogram (VFH+) Sector Discretization
- Divide the local $360^{\circ}$ space around the robot into 36 angular sectors of $10^{\circ}$ each (indices $0 \dots 35$).
- For each sector $i$, calculate its matching center angle:
  $$\theta_i = i \cdot 10^{\circ}$$
- For each active obstacle in the costmap, compute its distance $d$ and angle $\theta$ relative to the robot's current estimated pose.
- If $d$ is within the active sensing threshold, calculate the obstacle density weight:
  $$w = \frac{a - b \cdot d}{d}$$
  where $a = 2.0, b = 0.5$ (tunable parameters to adjust avoidance sensitivity).
- Accumulate the weight $w$ into the sector bin $i$ where $\theta$ falls.

### 2. Sector Smoothing
- Smooth sector bins to account for the physical robot width (prevent entering narrow gaps):
  $$H'_i = \frac{H_{i-1} + 2 \cdot H_i + H_{i+1}}{4}$$

### 3. Valley Selection & Steering Detour
- Find consecutive sectors ("valleys") where the smoothed obstacle density $H'_i$ is below a low threshold (e.g., $0.15$).
- Select the sector center angle $\theta_{detour}$ within the valleys that is closest to the target waypoint heading $\theta_{target}$.
- Blending: Adjust the target steering direction of the robot towards $\theta_{detour}$, modifying the raw drive velocity vector output from the path controller:
  $$v_{x, detour} = v_{\text{magnitude}} \cdot \cos(\theta_{detour})$$
  $$v_{y, detour} = v_{\text{magnitude}} \cdot \sin(\theta_{detour})$$

## Verification Strategy
- Create a closed-loop integration simulator test where an obstacle is placed directly between the robot's starting position and its target path waypoint.
- Verify that the controller calculates safe detour steering vectors and dynamically reroutes around the obstacle to complete the path cleanly without collision.
