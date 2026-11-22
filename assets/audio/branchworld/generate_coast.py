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


def shower(rng, count):
    """An ascending wash with three quiet water-drop notes."""
    result = [0.0] * count
    for i, (low, middle, high) in enumerate(noise_bands(rng, count, (90, 400, 3400))):
        progress = i / count
        result[i] = (low + (middle - low) * 0.55 + (high - middle) * 0.15) * (
            math.sin(math.pi * progress) ** 2
        )
        seconds = i / RATE
        for offset, pitch in ((0.12, 572), (0.22, 708), (0.38, 827)):
            age = seconds - offset
            if age > 0:
                result[i] += 0.36 * min(age * 200, 1) * math.exp(-age * 15) * math.sin(TAU * pitch * age)
    return result


def fire(rng, count):
    result = [0.0] * count
    for i, (low, body, air) in enumerate(noise_bands(rng, count, (112, 670, 3200))):
        swell = 0.72 + 0.20 * math.sin(TAU * i * 3 / count) + 0.08 * math.cos(TAU * i * 7 / count)
        result[i] = (0.34 * low + 0.13 * (body - low) + 0.025 * (air - body)) * swell
    for _ in range(86):
        start = rng.randrange(count)
        pitch = rng.uniform(550, 2300)
        strength = rng.uniform(0.025, 0.31)
        duration = rng.randrange(70, 730)
        for i in range(duration):
            phase = i / duration
            decay = (1 - phase) ** 4 * min(1, i / 4)
            snap = 0.60 * rng.uniform(-1, 1) + 0.40 * math.sin(TAU * pitch * i / RATE)
            result[(start + i) % count] += strength * decay * snap
    return result


def thunder(rng, count):
    for i, (sub, low, middle, high) in enumerate(noise_bands(rng, count, (55, 160, 690, 2700))):
        t = i / RATE
        attack = min(t / 0.021, 1)
        fade = min(1, (count - i) / (0.28 * RATE))
        crack = attack * math.exp(-t * 21) * (0.50 * (middle - low) + 0.29 * (high - middle))
        roll = sum(math.exp(-(t - center) ** 2 / spread) * level
                   for center, spread, level in ((0.13, 0.009, 1), (0.38, 0.020, 0.79), (0.77, 0.069, 0.42)))
        yield (crack + 3.1 * (sub + 0.53 * (low - sub)) * roll * attack) * fade


def write_layer(name, generator, seconds, peak, seed):
    values = list(generator(random.Random(seed), round(seconds * RATE)))
    scale = peak * 32767 / max(abs(value) for value in values)
    pcm = array.array('h', (round(value * scale) for value in values))
    if sys.byteorder != 'little':
        pcm.byteswap()
    with wave.open(str(DESTINATION / f'{name}.wav'), 'wb') as output:
        output.setparams((1, 2, RATE, len(pcm), 'NONE', 'not compressed'))
        output.writeframes(pcm.tobytes())


if __name__ == '__main__':
    write_layer('coastal-surf', surf, 16, 0.58, 104729)
    write_layer('coastal-wind', wind, 18, 0.39, 104759)
    write_layer('coastal-rain', rain, 14, 0.54, 104761)
    write_layer('coastal-birds', birds, 17, 0.22, 104773)
    write_layer('spring-shower', shower, 0.85, 0.44, 104779)
    write_layer('ember-crackle', fire, 7.5, 0.62, 424242)
    write_layer('ember-thunder', thunder, 2.1, 0.49, 424243)
