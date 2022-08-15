package fr.pioupia.courserecorder;

public class Array {
    private long[] arr;
    public int count;

    public Array(int length) { arr = new long[length]; }

    public long[] toArray() {
        long[] tab = new long[count];
        System.arraycopy(arr, 0, tab, 0, count);
        return tab;
    }

    public long get(int index) {
        return arr[index];
    }

    public void push(long element)
    {

        if (arr.length == count) {
            long[] newArr = new long[2 * count];

            System.arraycopy(arr, 0, newArr, 0, count);

            arr = newArr;
        }

        arr[count++] = element;
    }
}
