package fr.pioupia.courserecorder;

public class Array {
    private long[] arr;
    public int count;

    // Note they can only be called through function

    // Method 1
    // Inside helper class
    // to compute length of an array
    public Array(int length) { arr = new long[length]; }

    public long[] toArray() {
        long[] tab = new long[count];
        for (int i = 0; i < count; i++) {
            tab[i] = arr[i];
        }
        return tab;
    }

    public long get(int index) {
        return arr[index];
    }

    // Method 3
    // Inside Helper class
    public void push(long element)
    {

        if (arr.length == count) {

            // Creating a new array double the size
            // of array declared above
            long[] newArr = new long[2 * count];

            // Iterating over new array using for loop
            for (int i = 0; i < count; i++) {
                newArr[i] = arr[i];
            }

            // Assigning new array to original array
            // created above
            arr = newArr;
        }

        arr[count++] = element;
    }
}
