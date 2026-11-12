/** 처리 중 화면을 보여 주는 최소 시간. 응답이 빨라도 눌렀다는 반응이 눈에 보이게 한다. */
export const ACTION_RESULT_MIN_MILLIS = 1_000;

const sleep = (millis: number) => new Promise<void>((resolve) => setTimeout(resolve, millis));

/**
 * [task]가 끝나고 [minMillis]도 지나야 결과를 돌려준다.
 * 실패는 기다리지 않고 바로 던져 오류 안내가 늦어지지 않게 한다.
 */
export async function withMinimumDuration<T>(task: Promise<T>, minMillis: number, wait: (millis: number) => Promise<void> = sleep): Promise<T> {
    const [result] = await Promise.all([task, wait(minMillis)]);
    return result;
}
