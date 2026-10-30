#version 330 core

in vec2 vUv;
out vec4 outColor;

uniform vec2 uResolution;
uniform float uSpin;
uniform float uInclination;
uniform float uAzimuth;
uniform float uCameraRoll;
uniform float uCameraDistance;
uniform float uExposure;
uniform float uTime;
uniform float uDiskOuter;
uniform float uFov;
uniform int uMaxSteps;
uniform int uDiskDirection;
uniform int uCinematic;

const float PI = 3.14159265358979323846;
const float R_ESCAPE = 20.0;
const float STEP_MAX = 0.45;
const float HORIZON_MARGIN = 0.05;
const float POLAR_EPSILON = 0.04;
const float AXIAL_PIXEL_FLOOR = 0.0007;

struct Derivative {
    vec4 x;
    float pTheta;
};

struct InverseMetric {
    float tt;
    float tPhi;
    float phiPhi;
    float rr;
    float thetaTheta;
};

float outerHorizon() {
    return 1.0 + sqrt(max(0.0, 1.0 - uSpin * uSpin));
}

float deltaKerr(float r) {
    return r * r - 2.0 * r + uSpin * uSpin;
}

float sigmaKerr(float r, float theta) {
    float c = cos(theta);
    return r * r + uSpin * uSpin * c * c;
}

float iscoRadius() {
    float a = uSpin * float(uDiskDirection);
    float z1 = 1.0 + pow(max(0.0, 1.0 - a * a), 1.0 / 3.0)
        * (pow(1.0 + a, 1.0 / 3.0) + pow(1.0 - a, 1.0 / 3.0));
    float z2 = sqrt(3.0 * a * a + z1 * z1);
    float signA = a < 0.0 ? -1.0 : 1.0;
    return 3.0 + z2 - signA
        * sqrt(max(0.0, (3.0 - z1) * (3.0 + z1 + 2.0 * z2)));
}

InverseMetric inverseMetric(float r, float theta) {
    r = max(r, outerHorizon() + HORIZON_MARGIN * 0.25);
    theta = clamp(theta, POLAR_EPSILON, PI - POLAR_EPSILON);
    float s = sin(theta);
    float s2 = s * s;
    float sigma = sigmaKerr(r, theta);
    float delta = max(deltaKerr(r), 1e-8);
    float a2 = uSpin * uSpin;
    float A = (r * r + a2) * (r * r + a2) - a2 * delta * s2;
    float B = sigma * delta;
    InverseMetric g;
    g.tt = -A / B;
    g.tPhi = -2.0 * uSpin * r / B;
    g.phiPhi = (delta - a2 * s2) / max(B * s2, 1e-12);
    g.rr = delta / sigma;
    g.thetaTheta = 1.0 / sigma;
    return g;
}

float hamiltonian(float r, float theta, float pTime, float pRadial,
                  float pTheta, float pPhi) {
    InverseMetric g = inverseMetric(r, theta);
    return 0.5 * (g.tt * pTime * pTime + 2.0 * g.tPhi * pTime * pPhi
        + g.phiPhi * pPhi * pPhi + g.rr * pRadial * pRadial
        + g.thetaTheta * pTheta * pTheta);
}

void regularizePolar(inout vec4 state, inout float polarMomentum) {
    if (state.y < 0.0) {
        state.y = -state.y;
        polarMomentum = -polarMomentum;
        state.z += PI;
    } else if (state.y > PI) {
        state.y = 2.0 * PI - state.y;
        polarMomentum = -polarMomentum;
        state.z += PI;
    }
    state.y = clamp(state.y, POLAR_EPSILON, PI - POLAR_EPSILON);
}

void projectRadialNullConstraint(inout vec4 state, float pTime,
                                 float pTheta, float pPhi) {
    InverseMetric g = inverseMetric(state.x, state.y);
    float nonRadial = g.tt * pTime * pTime + 2.0 * g.tPhi * pTime * pPhi
        + g.phiPhi * pPhi * pPhi + g.thetaTheta * pTheta * pTheta;
    float radialSquared = -nonRadial / g.rr;
    if (radialSquared >= 0.0 && !isnan(radialSquared) && !isinf(radialSquared)) {
        float radialSign = state.w < 0.0 ? -1.0 : 1.0;
        state.w = radialSign * sqrt(radialSquared);
    }
}

Derivative geodesicDerivative(vec4 x, float pTheta, float pTime, float pPhi) {
    regularizePolar(x, pTheta);
    float r = max(x.x, outerHorizon() + HORIZON_MARGIN * 0.25);
    float theta = clamp(x.y, POLAR_EPSILON, PI - POLAR_EPSILON);
    float s = sin(theta);
    float c = cos(theta);
    float s2 = s * s;
    float r2 = r * r;
    float a2 = uSpin * uSpin;
    float sigma = sigmaKerr(r, theta);
    float delta = max(deltaKerr(r), 1e-8);
    float deltaR = 2.0 * r - 2.0;
    float sigmaR = 2.0 * r;
    float sigmaTheta = -2.0 * a2 * s * c;
    float A = (r2 + a2) * (r2 + a2) - a2 * delta * s2;
    float AR = 4.0 * r * (r2 + a2) - a2 * deltaR * s2;
    float ATheta = -2.0 * a2 * delta * s * c;
    float B = sigma * delta;
    float BR = sigmaR * delta + sigma * deltaR;
    float BTheta = sigmaTheta * delta;
    float C = delta - a2 * s2;
    float CR = deltaR;
    float CTheta = -2.0 * a2 * s * c;
    float invB2 = 1.0 / (B * B);

    InverseMetric g = inverseMetric(r, theta);
    float gttR = -(AR * B - A * BR) * invB2;
    float gttTheta = -(ATheta * B - A * BTheta) * invB2;
    float gtPhiR = -2.0 * uSpin / B + 2.0 * uSpin * r * BR * invB2;
    float gtPhiTheta = 2.0 * uSpin * r * BTheta * invB2;
    float gppR = CR / (B * s2) - C * BR / (B * B * s2);
    float gppTheta = CTheta / (B * s2)
        - C * (BTheta * s2 + 2.0 * B * s * c) / (B * B * s2 * s2);
    float grrR = deltaR / sigma - delta * sigmaR / (sigma * sigma);
    float grrTheta = -delta * sigmaTheta / (sigma * sigma);
    float gThetaThetaR = -sigmaR / (sigma * sigma);
    float gThetaThetaTheta = -sigmaTheta / (sigma * sigma);

    float twiceH = g.tt * pTime * pTime + 2.0 * g.tPhi * pTime * pPhi
        + g.phiPhi * pPhi * pPhi + g.rr * x.w * x.w
        + g.thetaTheta * pTheta * pTheta;
    float dHdr = 0.5 * (gttR * pTime * pTime + 2.0 * gtPhiR * pTime * pPhi
        + gppR * pPhi * pPhi + grrR * x.w * x.w
        + gThetaThetaR * pTheta * pTheta);
    float dHdTheta = 0.5 * (gttTheta * pTime * pTime
        + 2.0 * gtPhiTheta * pTime * pPhi + gppTheta * pPhi * pPhi
        + grrTheta * x.w * x.w + gThetaThetaTheta * pTheta * pTheta);
    Derivative result;
    result.x = vec4(g.rr * x.w, g.thetaTheta * pTheta,
        g.tPhi * pTime + g.phiPhi * pPhi, -dHdr);
    result.pTheta = -dHdTheta;
    if (isnan(twiceH) || isinf(twiceH)) {
        result.x = vec4(0.0);
        result.pTheta = 0.0;
    }
    return result;
}

vec4 shiftedX(vec4 x, Derivative d, float amount) {
    return x + amount * d.x;
}

float shiftedP(float p, Derivative d, float amount) {
    return p + amount * d.pTheta;
}

void integrateStep(inout vec4 x, inout float pTheta,
                   float pTime, float pPhi, float stepSize) {
    Derivative k1 = geodesicDerivative(x, pTheta, pTime, pPhi);
    vec4 x2 = shiftedX(x, k1, stepSize * 0.5);
    float pTheta2 = shiftedP(pTheta, k1, stepSize * 0.5);
    regularizePolar(x2, pTheta2);
    Derivative k2 = geodesicDerivative(x2, pTheta2, pTime, pPhi);
    vec4 x3 = shiftedX(x, k2, stepSize * 0.5);
    float pTheta3 = shiftedP(pTheta, k2, stepSize * 0.5);
    regularizePolar(x3, pTheta3);
    Derivative k3 = geodesicDerivative(x3, pTheta3, pTime, pPhi);
    vec4 x4 = shiftedX(x, k3, stepSize);
    float pTheta4 = shiftedP(pTheta, k3, stepSize);
    regularizePolar(x4, pTheta4);
    Derivative k4 = geodesicDerivative(x4, pTheta4, pTime, pPhi);
    x += stepSize / 6.0 * (k1.x + 2.0 * k2.x + 2.0 * k3.x + k4.x);
    pTheta += stepSize / 6.0
        * (k1.pTheta + 2.0 * k2.pTheta + 2.0 * k3.pTheta + k4.pTheta);
}

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

vec3 proceduralSky(vec3 direction) {
    direction = normalize(direction);
    float theta = acos(clamp(direction.y, -1.0, 1.0));
    float phi = atan(direction.z, direction.x);
    vec3 cell = floor(direction * 260.0);
    float starSeed = hash31(cell);
    vec3 randomPosition = vec3(hash31(cell + 1.7), hash31(cell + 5.3), hash31(cell + 9.1));
    vec3 offset = fract(direction * 260.0) - randomPosition;
    float star = 1.0 - smoothstep(0.003, 0.025, length(offset));
    star *= step(0.988, starSeed);
    float brightStar = step(0.9988, starSeed) * 1.7;
    float nebula = 0.012 + 0.035 * pow(max(0.0, sin(theta * 9.0 + sin(phi * 3.0))), 5.0);
    return vec3(0.11, 0.15, 0.23) * nebula
        + vec3(0.55, 0.66, 0.86) * star * (0.8 + brightStar);
}

void main() {
    vec2 screen = (gl_FragCoord.xy / uResolution) * 2.0 - 1.0;
    screen.x *= uResolution.x / uResolution.y;
    float fovScale = tan(uFov * 0.5);
    float screenX = screen.x;
    float screenY = -screen.y;
    float rollCos = cos(uCameraRoll);
    float rollSin = sin(uCameraRoll);
    float sx = (screenX * rollCos - screenY * rollSin) * fovScale;
    float sy = (screenX * rollSin + screenY * rollCos) * fovScale;
    float directionNorm = inversesqrt(1.0 + sx * sx + sy * sy);
    float nRadial = -directionNorm;
    float nTheta = sy * directionNorm;
    float nPhi = sx * directionNorm;
    if (abs(nPhi) < AXIAL_PIXEL_FLOOR) {
        nPhi = nPhi < 0.0 ? -AXIAL_PIXEL_FLOOR : AXIAL_PIXEL_FLOOR;
        float correctedNorm = inversesqrt(nRadial * nRadial
            + nTheta * nTheta + nPhi * nPhi);
        nRadial *= correctedNorm;
        nTheta *= correctedNorm;
        nPhi *= correctedNorm;
    }

    float r0 = uCameraDistance;
    float theta0 = clamp(uInclination, 0.035, PI - 0.035);
    float phi0 = uAzimuth;
    float delta0 = deltaKerr(r0);
    float sigma0 = sigmaKerr(r0, theta0);
    float s0 = sin(theta0);
    float a2 = uSpin * uSpin;
    float A0 = (r0 * r0 + a2) * (r0 * r0 + a2)
        - a2 * delta0 * s0 * s0;
    float alpha = sqrt(max(1e-8, sigma0 * delta0 / A0));
    float omega = 2.0 * uSpin * r0 / A0;
    float gPhiPhi = A0 * s0 * s0 / sigma0;

    float contravTime = -1.0 / alpha;
    float contravRadial = nRadial * sqrt(delta0 / sigma0);
    float contravTheta = nTheta / sqrt(sigma0);
    float contravPhi = omega * contravTime + nPhi / sqrt(gPhiPhi);
    float covTime = -(1.0 - 2.0 * r0 / sigma0) * contravTime
        - 2.0 * uSpin * r0 * s0 * s0 / sigma0 * contravPhi;
    float covPhi = -2.0 * uSpin * r0 * s0 * s0 / sigma0 * contravTime
        + gPhiPhi * contravPhi;
    float covRadial = sigma0 / delta0 * contravRadial;
    float covTheta = sigma0 * contravTheta;

    vec4 x = vec4(r0, theta0, phi0, covRadial);
    float pTheta = covTheta;
    float pTime = covTime;
    float pPhi = covPhi;
    vec3 radialBasis = vec3(sin(theta0) * cos(phi0), cos(theta0), sin(theta0) * sin(phi0));
    vec3 thetaBasis = vec3(cos(theta0) * cos(phi0), -sin(theta0),
        cos(theta0) * sin(phi0));
    vec3 phiBasis = vec3(-sin(phi0), 0.0, cos(phi0));
    vec3 cameraRayDirection = normalize(
        nRadial * radialBasis + nTheta * thetaBasis + nPhi * phiBasis);
    float innerRadius = max(iscoRadius(), outerHorizon() + 0.05);
    float diskOuter = max(innerRadius + 0.5, uDiskOuter);
    vec3 radiance = vec3(0.0);
    bool captured = false;
    bool escaped = false;
    bool polarAxisEncountered = false;
    int diskCrossings = 0;
    float initialHamiltonian = hamiltonian(x.x, x.y, pTime, x.w, pTheta, pPhi);

    for (int i = 0; i < 420; ++i) {
        if (i >= uMaxSteps || captured || escaped || diskCrossings >= 6) {
            break;
        }
        polarAxisEncountered = polarAxisEncountered || x.y < 0.12 || x.y > PI - 0.12;
        float oldRadius = x.x;
        float oldTheta = x.y;
        float horizonFactor = clamp(deltaKerr(x.x) / 0.15, 0.05, 1.0);
        float radialStepScale = mix(0.012, 0.04, smoothstep(3.0, 12.0, x.x));
        float stepSize = clamp(radialStepScale * max(x.x, 1.0), 0.001, STEP_MAX)
            * horizonFactor;
        stepSize *= clamp(abs(sin(x.y)) / 0.3, 0.12, 1.0);
        integrateStep(x, pTheta, pTime, pPhi, stepSize);

        regularizePolar(x, pTheta);

        if (isnan(x.x) || isinf(x.x) || isnan(x.y) || isinf(x.y)
                || isnan(x.z) || isinf(x.z)
                || isnan(x.w) || isinf(x.w) || isnan(pTheta) || isinf(pTheta)) {
            break;
        }
        if ((i & 1) == 0) {
            projectRadialNullConstraint(x, pTime, pTheta, pPhi);
        }
        if (x.x <= outerHorizon() + HORIZON_MARGIN) {
            captured = true;
            break;
        }
        if (x.x >= R_ESCAPE && x.w > 0.0) {
            escaped = true;
            break;
        }

        bool crossedEquator = (oldTheta - PI * 0.5) * (x.y - PI * 0.5) <= 0.0
            && abs(x.y - oldTheta) > 1e-7;
        if (crossedEquator) {
            float fraction = clamp((PI * 0.5 - oldTheta) / (x.y - oldTheta), 0.0, 1.0);
            float hitRadius = mix(oldRadius, x.x, fraction);
            if (hitRadius >= innerRadius && hitRadius <= diskOuter) {
                float hitPhi = x.z;
                float orbitDirection = float(uDiskDirection);
                if (diskCrossings < 8) {
                    float omegaDisk = orbitDirection
                        / (pow(hitRadius, 1.5) + uSpin * orbitDirection);
                    float emitterTime = (pow(hitRadius, 1.5) + uSpin * orbitDirection)
                        / (pow(hitRadius, 0.75) * sqrt(max(1e-6,
                            pow(hitRadius, 1.5) - 3.0 * sqrt(hitRadius)
                            + 2.0 * uSpin * orbitDirection)));
                    float lambda = pPhi / max(pTime, 1e-6);
                    float g = 1.0 / max(0.05, emitterTime * (1.0 + omegaDisk * lambda));
                    float radial = innerRadius / hitRadius;
                    float temperature = pow(radial, 0.75);
                    float emissivity = pow(radial, 3.0) * max(0.0, 1.0 - sqrt(radial));
                    float colorMix = clamp((temperature - 0.28) * 1.3, 0.0, 1.0);
                    vec3 emission = mix(vec3(0.48, 0.075, 0.018),
                        vec3(1.0, 0.64, 0.34), colorMix);
                    emission = mix(emission, vec3(1.0, 0.92, 0.79),
                        smoothstep(0.76, 1.0, temperature));
                    float turbulence = 0.9 + 0.1 * sin(hitPhi * 19.0 + hitRadius * 2.7 - uTime * 0.8)
                        * sin(hitPhi * 31.0 - hitRadius * 4.1 + uTime * 0.55);
                    radiance += emission * emissivity * pow(max(0.02, g), 4.0)
                        * turbulence * 34.0;
                    diskCrossings++;
                }
            }
        }

        if ((i & 3) == 3) {
            float hamiltonianError = abs(hamiltonian(
                    x.x, x.y, pTime, x.w, pTheta, pPhi) - initialHamiltonian);
            if (hamiltonianError > 0.15) {
                break;
            }
        }
    }

    if (captured && polarAxisEncountered) {
        captured = false;
    }

    if (escaped) {
        radiance += proceduralSky(vec3(sin(x.y) * cos(x.z), cos(x.y), sin(x.y) * sin(x.z)));
    } else if (!captured) {
        radiance += proceduralSky(cameraRayDirection);
    }

    radiance *= exp2(uExposure);
    if (uCinematic != 0) {
        radiance = 1.0 - exp(-radiance * 1.6);
        radiance = pow(max(radiance, vec3(0.0)), vec3(1.0 / 2.2));
    } else {
        radiance = radiance / (1.0 + radiance);
    }
    outColor = vec4(radiance, 1.0);
}
