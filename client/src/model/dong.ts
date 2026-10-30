/** 서울 행정동. [district]는 동이 속한 자치구 이름이다. */
export type Dong = {
    code: string;
    name: string;
    district: string;
};

export function dongDisplayName(dong: Dong): string {
    return `${dong.district} ${dong.name}`;
}

const koreanCollator = new Intl.Collator('ko-KR');

/** 구 → 동 순서로 고르거나 이름으로 찾을 수 있게 정리한 서울 행정동 목록. */
export class DongCatalog {
    private readonly dongsByCode: Map<string, Dong>;
    private readonly dongsByDistrict: Map<string, Dong[]>;
    readonly districts: string[];

    constructor(dongs: Dong[]) {
        this.dongsByCode = new Map(dongs.map((dong) => [dong.code, dong]));
        this.dongsByDistrict = new Map();
        dongs.forEach((dong) => {
            const group = this.dongsByDistrict.get(dong.district) ?? [];
            group.push(dong);
            this.dongsByDistrict.set(dong.district, group);
        });
        this.dongsByDistrict.forEach((group) => group.sort((left, right) => koreanCollator.compare(left.name, right.name)));
        this.districts = [...this.dongsByDistrict.keys()].sort(koreanCollator.compare);
    }

    dongsIn(district: string): Dong[] {
        return this.dongsByDistrict.get(district) ?? [];
    }

    find(code: string | null | undefined): Dong | null {
        return code ? this.dongsByCode.get(code) ?? null : null;
    }

    /** 동 이름이나 "구 동" 이름에 검색어가 들어간 동을 찾는다. 띄어쓰기는 무시한다. */
    search(query: string): Dong[] {
        const keyword = query.replace(/\s/g, '');
        if (!keyword) return [];
        return this.districts
            .flatMap((district) => this.dongsIn(district))
            .filter((dong) => (dong.district + dong.name).includes(keyword));
    }
}
