# Release Notes

### 2.0.2 (2023-02-13)
* Library
    * Fix drawer in a stopped Activity would not stay open when the Activity recreated.
    * Fix bug where custom RTL properties are ignored when RTL is not supported and the left
      and right ones are defined.
    * Optimize child measurement logic of SlidingDrawerLayout.

### 2.0.1 (2021-11-25)
* Library
    * Fix inaccuracy of the `Utils#isLayoutDirectionResolved` method on SDKs 17 & 18.
    * Fix a NPE crash issue (null `mShownDrawer` accessed in `addFocusables()`) occurred on
      platform versions prior to `JELLY_BEAN_MR2` (18) where the `addFocusables()` method
      will be called immediately when visibility changes from visible to invisible
      for the focused shown drawer view.
    * Fix rtl properties not resolved on `JELLY_BEAN` and lower version SDKs.
    * Remove the dependency on `jcenter()`

### 2.0 (2021-03-02)
* Library
    * Add missing matrix/clip state restoration for Canvas.

### 1.2.4 (2020-09-02)
* Library
    * Add a visibility check for the drawer the width percentage for which changes to see if it is
      in the layout before requesting a new layout.
    * Add fraction value support for `widthPercent_*Drawer` attributes.

### 1.2.3 (2019-11-21)
* Library
    * Support for fullscreen drawers, i.e., the drawer width percentage can be 1.0f.

### 1.2.2 (2019-06-11)
* Library
    * Fix a bug that the layout should re-resolve the width percentages and touch abilities of
      the drawer when the layout direction changed due to external program code but it actually not.

### 1.2.1 (2018-12-15)
* Library
    * Stability improvements.
    * Use `offsetLeftAndRight()` to scroll through the contents instead of `setTranslationX()`
      for lower coupling.

### 1.2 (2018-11-25)
* Library
    * Bug fixes.
    * Can save the state whether there is a drawer open or not.
    * Support for Accessibility.

### 1.1 (2018-11-11)
* Library
    * Support for customizing the lasting time of the scrolling animations for the drawers.
    * Better support layouts in right-to-left direction.
    * No need for writing the drawers in front of the content view in your xml layout file so that
      they end up on the lower level of the content, which, for convenient, is guaranteed by default
      from this release, regardless of their order of writing.
    * Set a hardware layer for the drawer as it scrolls and a hardware layer can also be set for
      the content view as needed.
    * Support for using `ViewStub` as a lazy inflated (lazily instaniated and rendered) drawer.
    * Auto-monitor the back key (can be disabled).
    * Bug fixes.

### 1.0.1 (2018-10-11)
* Library
    * Enable to disable drawer or not.

### 1.0 (2018-06-29)
* No notes provided.