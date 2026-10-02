# Native launcher design pass

The app remains a native WPF desktop app with its bundled runtime. No browser engine, paid template or extra framework was added. The supplied logos are displayed with proportional scaling; the premium banner uses a presentation viewport around its logo, and SKlauncher retains its proportions.

The design uses a quiet charcoal surface, a slightly darker brand sidebar, restrained yellow actions, muted green detected-launcher status, and a visible scrollbar thumb. Related controls share spacing and rounded corners. Language stays on the main page, while file selection and maintenance are in Settings. Destructive actions use a themed confirmation with a safe Cancel choice. Keyboard focus remains visible. The empty draggable title bar keeps ordinary minimize, maximize/restore and close controls.

These choices follow Microsoft's [Windows design guidance](https://learn.microsoft.com/en-us/windows/apps/design/guidelines-overview), [navigation guidance](https://learn.microsoft.com/en-us/windows/apps/design/basics/navigation-basics), and [dialog guidance](https://learn.microsoft.com/en-us/windows/apps/develop/ui/controls/dialogs-and-flyouts/dialogs). The implementation uses WPF equivalents, not WinUI ContentDialog APIs.

The requested [GetLayers](https://www.getlayers.ai/), [Landdding](https://landdding.com/), [Motion](https://www.motionin.design/) and [CollectUI](https://collectui.com/) collections informed the reference review. No protected templates, code or commercial assets were copied. Pageflows could not be retrieved during this review.

Windows-owned SmartScreen prompts and file pickers are not simulated or hidden. App-owned error and confirmation dialogs share the launcher theme. File Company metadata is separate from the verified publisher described in Microsoft's [SmartScreen guidance](https://learn.microsoft.com/en-us/windows/apps/package-and-deploy/smartscreen-reputation).

## 0.7.0 follow-up

Modal owners dim until their child closes, including nested confirmations. Child windows get distinct surfaces and a visible outline. Text inherits the action foreground instead of a global white TextBlock style. Green indicates launching and active progress; red marks removal, failure and cancellation; yellow remains the brand/continue and completed-progress accent. Labels still communicate the action, so color is not the only cue. Focus strokes sit inside the control bounds to avoid clipping in scroll containers.

## 0.8.0 follow-up

Play moves below the proportionally scaled logo and name. Its subtitle identifies the chosen launcher. It is enabled and green only when both launcher detection and pack readiness succeed. The slogan is removed. Buttons, input fields, menus and help-section containers use a shared six-pixel outer radius. Stronger green and red actions retain readable text.

Input modality distinguishes pointer interaction from keyboard navigation. Mouse interaction clears keyboard focus decoration; Tab and navigation keys restore it. Closing a modal propagates its last input modality to its owner, preventing a stale button outline. A selected launcher still retains its deliberate selection border.

The [anti-slop reference](https://github.com/miqdadbadjuber/anti-slop) informed the focus on useful hierarchy, intentional spacing and consistent controls. No source or assets were copied from it.
