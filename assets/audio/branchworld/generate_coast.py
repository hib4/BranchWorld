"""Generate BranchWorld's original, seamless PCM soundscape from a fixed seed."""

import array
import math
from pathlib import Path
import random
import sys
import wave


RATE = 24_000
TAU = 2 * math.pi
DESTINATION = Path(__file__).resolve().parents[3] / 'shared/src/commonMain/composeResources/files/sounds'


def noise_bands(rng, count, frequencies):
    """Separate low and high components through one-pole filters in a circular buffer."""
    raw = [rng.uniform(-1, 1) for _ in range(count)]
    values = [0.0] * len(frequencies)
    coefficients = [1 - math.exp(-TAU * hz / RATE) for hz in frequencies]
    for sample in raw[-RATE:]:
        for band, coefficient in enumerate(coefficients):
            values[band] += coefficient * (sample - values[band])
    for sample in raw:
        for band, coefficient in enumerate(coefficients):
            values[band] += coefficient * (sample - values[band])
        yield values


def surf(rng, count):
    for i, (rumble, foam, hiss) in enumerate(noise_bands(rng, count, (85, 890, 3200))):
        t = i / count * TAU
        swell = (0.5 + 0.5 * math.sin(3 * t + 0.2 * math.sin(t))) ** 1.8
        wash = (0.5 + 0.5 * math.sin(3 * t - 0.82 + 0.23 * math.sin(t))) ** 2.6
        yield (rumble * (0.68 + swell * 1.7) + (foam - rumble) * (0.18 + swell * 0.48)
               + (hiss - foam) * (0.055 + wash * 0.28))


def wind(rng, count):
    for i, (low, leaf, high) in enumerate(noise_bands(rng, count, (24, 270, 1900))):
        t = i / count * TAU
        gust = (0.5 + 0.5 * math.sin(3 * t - 0.7 * math.sin(2 * t))) ** 2
        rustle = (0.5 + 0.5 * math.sin(11 * t + 0.72 * math.sin(5 * t))) ** 2
        yield low * (0.8 + gust * 1.1) + (leaf - low) * (0.20 + gust * 0.22) + (high - leaf) * gust * rustle * 0.29


def rain(rng, count):
    result = [0.0] * count
    for i, (base, middle, high) in enumerate(noise_bands(rng, count, (120, 920, 4800))):
        result[i] = base * 0.28 + (middle - base) * 0.36 + (high - middle) * 0.18
    for _ in range(470):
        start = rng.randrange(count)
        frequency = rng.uniform(750, 2900)
        strength = rng.uniform(0.018, 0.114)
        duration = rng.randrange(106, 480)
        for i in range(duration):
            phase = i / duration
            decay = (1 - phase) ** 3 * min(i / 9, 1)
            sound = math.sin(TAU * frequency * i / RATE * (1 - phase * 0.09))
            result[(start + i) % count] += strength * decay * (sound * 0.66 + rng.uniform(-0.34, 0.34))
    return result


def birds(rng, count):
    result = [0.0] * count
    for start, calls, pitch in ((1.62, 3, 2110), (6.24, 2, 2570), (12.3, 4, 1870)):
        for note in range(calls):
            duration = rng.uniform(0.096, 0.22)
            frequency = pitch * rng.uniform(0.90, 1.12)
            stride = int((start + note * 0.29) * RATE)
            for i in range(int(duration * RATE)):
                t = i / RATE
                p = t / duration
                envelope = math.sin(math.pi * p) ** 2
                sweep = frequency * t * (0.82 + 0.51 * p - 0.37 * p * p)
                syllable = math.sin(TAU * sweep + 0.28 * math.sin(TAU * 18 * t))
                overtone = math.sin(TAU * sweep * 2.02) * 0.14
                result[(stride + i) % count] += (syllable + overtone) * envelope
    return result


def write_layer(name, generator, seconds, peak, seed):
    values = list(generator(random.Random(seed), round(seconds * RATE)))
    scale = peak * 32767 / max(abs(value) for value in values)
    pcm = array.array('h', (round(value * scale) for value in values))
    if sys.byteorder != 'little':
        pcm.byteswap()
    with wave.open(str(DESTINATION / f'coastal-{name}.wav'), 'wb') as output:
        output.setparams((1, 2, RATE, len(pcm), 'NONE', 'not compressed'))
        output.writeframes(pcm.tobytes())


if __name__ == '__main__':
    write_layer('surf', surf, 16, 0.58, 104729)
    write_layer('wind', wind, 18, 0.39, 104759)
    write_layer('rain', rain, 14, 0.54, 104761)
    write_layer('birds', birds, 17, 0.22, 104773)
