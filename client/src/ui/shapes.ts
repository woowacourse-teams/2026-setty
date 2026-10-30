import type { CSSProperties } from 'react';

/**
 * Android 앱이 쓰는 Material Expressive 도형(MaterialShapes)을 clip-path 다각형으로 근사한다.
 * 꽃잎 수가 [lobes]이고 안쪽으로 [depth]만큼 들어가는 둥근 물결 테두리를 만든다.
 */
function wavyPolygon(lobes: number, depth: number): string {
    const pointCount = 144;
    const points: string[] = [];
    for (let index = 0; index < pointCount; index += 1) {
        const angle = (index / pointCount) * Math.PI * 2 - Math.PI / 2;
        const radius = 0.5 * (1 - depth) + 0.5 * depth * Math.cos(lobes * (angle + Math.PI / 2));
        const x = 50 + 100 * radius * Math.cos(angle);
        const y = 50 + 100 * radius * Math.sin(angle);
        points.push(`${x.toFixed(2)}% ${y.toFixed(2)}%`);
    }
    return `polygon(${points.join(', ')})`;
}

const cookie9 = wavyPolygon(9, 0.1);
const cookie6 = wavyPolygon(6, 0.12);
const softBurst = wavyPolygon(10, 0.2);

export type ShapeName = 'cookie9' | 'cookie6' | 'softBurst' | 'square';

export function shapeStyle(shape: ShapeName): CSSProperties {
    switch (shape) {
        case 'cookie9':
            return { clipPath: cookie9 };
        case 'cookie6':
            return { clipPath: cookie6 };
        case 'softBurst':
            return { clipPath: softBurst };
        case 'square':
            return { borderRadius: '30%' };
    }
}
