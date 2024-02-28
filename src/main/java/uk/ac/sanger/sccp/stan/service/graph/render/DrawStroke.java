/*
 * Copyright (c) 2016 Genome Research Ltd. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.sanger.sccp.stan.service.graph.render;

import java.util.Arrays;

/**
 * A specification for a line stroke, which may be dashed.
 * @author dr6
 */
public class DrawStroke {
    private int width;
    private int[] dashArray;

    public DrawStroke(int width, int... dashes) {
        this.width = width;
        this.dashArray = Arrays.copyOf(dashes, dashes.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DrawStroke that = (DrawStroke) o;
        return (this.width == that.width && Arrays.equals(this.dashArray, that.dashArray));
    }

    @Override
    public int hashCode() {
        return 31*width + Arrays.hashCode(dashArray);
    }

    public int getWidth() {
        return this.width;
    }

    public int[] getDashArray() {
        return this.dashArray;
    }
}
