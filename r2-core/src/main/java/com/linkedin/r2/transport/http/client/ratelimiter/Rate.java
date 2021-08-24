/*
   Copyright (c) 2019 LinkedIn Corp.

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.linkedin.r2.transport.http.client.ratelimiter;

import java.util.Arrays;


/**
 * An immutable implementation of rate as number of events per period of time in milliseconds.
 * In addition, a {@code burst} parameter is used to indicate the maximum number of permits can
 * be issued at a time. To satisfy the burst requirement, {@code period} might adjusted if
 * necessary. The minimal period is one millisecond. If the specified events per period cannot
 * satisfy the burst, an {@link IllegalArgumentException} will be thrown.
 */
public class Rate
{
  public static final Rate MAX_VALUE = new Rate(Integer.MAX_VALUE, 1, Integer.MAX_VALUE);
  public static final Rate ZERO_VALUE = new Rate(0, 1, 1);
  public static final float PRECISION_TARGET = 0.95f;

  private final double _events;
  private final double _period;

  /**
   * Constructs a new instance of Rate.
   *
   * @param events Number of events per period.
   * @param period Time period length in milliseconds.
   * @param burst  Maximum number of events allowed simultaneously.
   */
  public Rate(double events, double period, int burst)
  {
    if (burst < events)
    {
      double newPeriod = period * burst / events;
      if (period == 0 || burst == 0)
      {
        String message = String.format(
          "Configured rate of %f events per %f ms cannot satisfy the requirement of %d burst events at a time",
          events, period, burst);
        throw new IllegalArgumentException(message);
      }

      // if it's under 1 ms, we can just increase the number of events that are consumable every ms
      if (newPeriod < 1)
      {
        burst = (int) (burst * (1 / newPeriod));
        newPeriod = 1;
      }

      _events = burst;
      _period = newPeriod;

    }
    else
    {
      _events = events;
      _period = period;
    }
  }

  /**
   * Gets the number of events to be executed in a period.
   * Result of getEvents and getPeriod calls will be adjusted if the
   * int representation of the underlying _events float is less than
   * the PRECISION_TARGET.
   *
   * @return Events in period.
   */
  public int getEvents()
  {
    return (int) (_events * getPrecisionMultiplier());
  }

  /**
   * Gets the number of events to be executed in a period. Not rounded
   *
   * @return Events in period.
   */
  public double getEventsRaw()
  {
    return _events;
  }

  /**
   * Gets period in Milliseconds.
   * Result of getEvents and getPeriod calls will be adjusted if the
   * int representation of the underlying _events float is less than
   * the PRECISION_TARGET.
   *
   * @return Period in milliseconds.
   */
  public long getPeriod()
  {
    return Math.round(_period * getPrecisionMultiplier());
  }

  /**
   * Gets period in Milliseconds. Not Rounded
   *
   * @return Period in milliseconds.
   */
  public double getPeriodRaw()
  {
    return _period;
  }

  /**
   * Determines multiplier to apply to results of getEvents and getPeriod
   * in an effort to maintain precision while returning int representations
   * of the underlying floats
   * @return multiplier that best achieves the PRECISION_TARGET such that
   *         the int representation of (events * multiplier) divided by
   *         the raw float of (events * multiplier) is greater than PRECISION_TARGET.
   */
  private int getPrecisionMultiplier()
  {
    for (int multiplier: Arrays.asList(1, 10, 100))
    {
      double eventsCandidate = _events * multiplier;
      if ((int) eventsCandidate / eventsCandidate > PRECISION_TARGET)
      {
        return multiplier;
      }
    }
    return 100;
  }
}
